package com.fooddelivery.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.ledger.client.BeneficiaryClient;
import com.fooddelivery.common.dto.ledger.BeneficiaryResponse;
import com.fooddelivery.ledger.dto.CreatePayoutRequest;
import com.fooddelivery.ledger.dto.PendingPayoutResponse;
import com.fooddelivery.ledger.entity.*;
import com.fooddelivery.ledger.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final PayoutLineRepository payoutLineRepository;
    private final ILedgerAccountRepository accountRepository;
    private final ILedgerEntryRepository entryRepository;
    private final BeneficiaryClient beneficiaryClient;
    private final OwnerNameResolver ownerNameResolver;
    private final PayoutStateMachine stateMachine;
    private final DoubleEntryLedgerService doubleEntryLedgerService;
    private final ObjectMapper objectMapper;

    @Value("${payouts.four-eyes:true}")
    private boolean fourEyesEnabled;

    @Transactional(readOnly = true)
    public List<PendingPayoutResponse> pending() {
        return pending(0, Integer.MAX_VALUE);
    }

    /**
     * The queue an administrator clears, largest unsettled amount first.
     *
     * <p>Paged on purpose: an unbounded list of every payee with a positive payable will not survive
     * real volume, and the admin only ever works the top of it.
     */
    @Transactional(readOnly = true)
    public List<PendingPayoutResponse> pending(int page, int size) {
        List<LedgerAccountType> payableTypes = List.of(LedgerAccountType.RESTAURANT_PAYABLE, LedgerAccountType.DRIVER_PAYABLE);
        List<LedgerAccount> accounts = accountRepository.findByOwnerTypeInAndBalanceGreaterThan(payableTypes, BigDecimal.ZERO);

        List<UUID> restaurantIds = new ArrayList<>();
        List<UUID> driverIds = new ArrayList<>();
        
        for (LedgerAccount acc : accounts) {
            if (acc.getOwnerType() == LedgerAccountType.RESTAURANT_PAYABLE) restaurantIds.add(acc.getOwnerId());
            else driverIds.add(acc.getOwnerId());
        }

        Map<UUID, OwnerNameResolver.ResolvedName> restaurantNames = ownerNameResolver.resolveDisplayNames("RESTAURANT", restaurantIds);
        Map<UUID, OwnerNameResolver.ResolvedName> driverNames = ownerNameResolver.resolveDisplayNames("DRIVER", driverIds);

        List<PendingPayoutResponse> responses = new ArrayList<>();
        for (LedgerAccount acc : accounts) {
            String payeeType = acc.getOwnerType() == LedgerAccountType.RESTAURANT_PAYABLE ? "RESTAURANT" : "DRIVER";
            UUID payeeId = acc.getOwnerId();

            List<Payout> pendingPayouts = payoutRepository.findByPayeeTypeAndPayeeIdAndStatusIn(
                    payeeType, payeeId, List.of(PayoutStatus.DRAFT, PayoutStatus.APPROVED));

            BigDecimal pendingAmount = pendingPayouts.stream()
                    .map(Payout::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal unsettledAmount = acc.getBalance().subtract(pendingAmount);

            if (unsettledAmount.compareTo(BigDecimal.ZERO) > 0) {
                BeneficiaryResponse beneficiary = null;
                try {
                    beneficiary = beneficiaryClient.getBeneficiary(payeeType, payeeId);
                } catch (Exception ex) {
                    log.warn("Failed to get beneficiary for {} {}: {}", payeeType, payeeId, ex.getMessage());
                }

                OwnerNameResolver.ResolvedName name = "RESTAURANT".equals(payeeType)
                        ? restaurantNames.get(payeeId) : driverNames.get(payeeId);
                if (name == null) {
                    name = OwnerNameResolver.ResolvedName.unresolved(payeeType, payeeId);
                }
                
                List<LedgerEntry> unsettledEntries = entryRepository.findUnsettledEntries(acc.getId(), OffsetDateTime.now());
                OffsetDateTime unsettledSince = unsettledEntries.stream()
                        .map(LedgerEntry::getCreatedAt)
                        .min(OffsetDateTime::compareTo)
                        .orElse(null);

                Payout lastPayout = payoutRepository.findFirstByPayeeTypeAndPayeeIdAndStatusOrderByPaidAtDesc(payeeType, payeeId, PayoutStatus.PAID).orElse(null);

                if (beneficiary == null) {
                    beneficiary = BeneficiaryResponse.builder()
                        .verified(false)
                        .source("UNAVAILABLE")
                        .build();
                }

                responses.add(PendingPayoutResponse.builder()
                        .payeeType(payeeType)
                        .payeeId(payeeId)
                        .displayName(name.displayName())
                        .nameResolved(name.resolved())
                        .unsettledAmount(unsettledAmount)
                        .unsettledSince(unsettledSince)
                        .lineCount(unsettledEntries.size())
                        .lastPayout(lastPayout)
                        .beneficiaryStatus(beneficiary)
                        .build());
            }
        }
        
        responses.sort((r1, r2) -> r2.getUnsettledAmount().compareTo(r1.getUnsettledAmount()));
        int from = Math.min((long) page * size > Integer.MAX_VALUE ? responses.size() : page * size, responses.size());
        int to = size == Integer.MAX_VALUE ? responses.size() : Math.min(from + size, responses.size());
        return responses.subList(from, to);
    }

    @Transactional
    public Payout create(CreatePayoutRequest request, String idempotencyKey, UUID adminId) {
        Optional<Payout> existing = payoutRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<Payout> existingDrafts = payoutRepository.findByPayeeTypeAndPayeeIdAndStatusIn(
                request.getPayeeType(), request.getPayeeId(), List.of(PayoutStatus.DRAFT, PayoutStatus.APPROVED));
        if (!existingDrafts.isEmpty()) {
            throw new IllegalStateException("PAYOUT_ALREADY_PENDING");
        }

        LedgerAccountType accountType = "RESTAURANT".equals(request.getPayeeType()) ? LedgerAccountType.RESTAURANT_PAYABLE : LedgerAccountType.DRIVER_PAYABLE;
        LedgerAccount account = accountRepository.findByOwnerIdAndOwnerTypeForUpdate(request.getPayeeId(), accountType)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));

        List<LedgerEntry> entries = entryRepository.findUnsettledEntries(account.getId(), request.getPeriodTo());
        
        BigDecimal amount = BigDecimal.ZERO;
        for (LedgerEntry e : entries) {
            if (e.getDirection() == TransactionDirection.CREDIT) {
                amount = amount.add(e.getAmount());
            } else {
                amount = amount.subtract(e.getAmount());
            }
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        BeneficiaryResponse beneficiary = beneficiaryClient.getBeneficiary(request.getPayeeType(), request.getPayeeId());
        if (beneficiary == null || (!beneficiary.isVerified() && !request.isForce())) {
            throw new IllegalStateException("Beneficiary not verified or missing");
        }

        // payouts.payee_display_name is the audit record of who was paid. A stand-in built from the
        // id is not a name, so refuse rather than persist one.
        OwnerNameResolver.ResolvedName payeeName =
                ownerNameResolver.resolveDisplayName(request.getPayeeType(), request.getPayeeId());
        if (!payeeName.resolved()) {
            throw new IllegalStateException("PAYEE_NAME_UNRESOLVED: cannot record a payout to "
                    + request.getPayeeType() + " " + request.getPayeeId()
                    + " without a name from the owning service");
        }

        UUID payoutId = UUID.randomUUID();
        String snapshot;
        try {
            snapshot = objectMapper.writeValueAsString(beneficiary);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize beneficiary", e);
        }

        UUID ledgerTransactionId = com.fooddelivery.common.util.DeterministicIdUtils.ledgerId("ledger-service", payoutId, "CREATE");
        LedgerLeg leg = new LedgerLeg(
                accountType, request.getPayeeId(),
                LedgerAccountType.PAYOUT_IN_TRANSIT, com.fooddelivery.common.constants.LedgerAccounts.PAYOUT_IN_TRANSIT,
                amount, ChargeCategory.PAYOUT_TRANSFER, "Payout CREATE", adminId.toString()
        );
        LedgerTransactionCommand txReq = new LedgerTransactionCommand(
                ledgerTransactionId, payoutId, "ledger-service", "CREATE", List.of(leg)
        );
        doubleEntryLedgerService.record(txReq);

        OffsetDateTime minDate = entries.stream().map(LedgerEntry::getCreatedAt).min(OffsetDateTime::compareTo).orElse(request.getPeriodTo());

        Payout payout = Payout.builder()
                .id(payoutId)
                .payeeType(request.getPayeeType())
                .payeeId(request.getPayeeId())
                .payeeDisplayName(payeeName.displayName())
                .periodFrom(minDate)
                .periodTo(request.getPeriodTo())
                .amount(amount)
                .status(PayoutStatus.DRAFT)
                .beneficiarySnapshot(snapshot)
                .createdBy(adminId)
                .idempotencyKey(idempotencyKey)
                .ledgerTransactionId(ledgerTransactionId)
                .build();
        
        payoutRepository.save(payout);

        List<PayoutLine> lines = entries.stream().map(e -> PayoutLine.builder()
                .id(UUID.randomUUID())
                .payoutId(payoutId)
                .ledgerEntryId(e.getId())
                .referenceId(e.getReferenceId())
                .category(e.getCategory())
                .direction(e.getDirection())
                .amount(e.getAmount())
                .entryCreatedAt(e.getCreatedAt())
                .active(true)
                .build()).collect(Collectors.toList());
        payoutLineRepository.saveAll(lines);

        return payout;
    }

    @Transactional
    public void approve(UUID payoutId, UUID adminId) {
        Payout payout = payoutRepository.findById(payoutId).orElseThrow();
        if (payout.getStatus() == PayoutStatus.APPROVED) {
            return;
        }
        if (fourEyesEnabled && adminId.equals(payout.getCreatedBy())) {
            throw new IllegalStateException("Four-eyes principle: cannot approve own payout");
        }
        stateMachine.transitionTo(payout, PayoutStatus.APPROVED);
        payout.setApprovedBy(adminId);
        payout.setApprovedAt(OffsetDateTime.now());
        payoutRepository.save(payout);
    }

    @Transactional
    public void markPaid(UUID payoutId, String bankReference, UUID adminId) {
        Payout payout = payoutRepository.findById(payoutId).orElseThrow();
        if (payout.getStatus() == PayoutStatus.PAID) {
            return;
        }
        stateMachine.transitionTo(payout, PayoutStatus.PAID);
        
        UUID settledTransactionId = com.fooddelivery.common.util.DeterministicIdUtils.ledgerId("ledger-service", payoutId, "PAID");
        LedgerLeg leg = new LedgerLeg(
                LedgerAccountType.PAYOUT_IN_TRANSIT, com.fooddelivery.common.constants.LedgerAccounts.PAYOUT_IN_TRANSIT,
                LedgerAccountType.BANK, com.fooddelivery.common.constants.LedgerAccounts.BANK,
                payout.getAmount(), ChargeCategory.PAYOUT_TRANSFER, "Payout PAID", adminId.toString()
        );
        LedgerTransactionCommand txReq = new LedgerTransactionCommand(
                settledTransactionId, payoutId, "ledger-service", "PAID", List.of(leg)
        );
        doubleEntryLedgerService.record(txReq);

        payout.setPaidBy(adminId);
        payout.setPaidAt(OffsetDateTime.now());
        payout.setBankReference(bankReference);
        payout.setSettledTransactionId(settledTransactionId);
        payoutRepository.save(payout);
    }

    @Transactional
    public void fail(UUID payoutId, String reason, UUID adminId) {
        Payout payout = payoutRepository.findById(payoutId).orElseThrow();
        if (payout.getStatus() == PayoutStatus.FAILED) {
            return;
        }
        stateMachine.transitionTo(payout, PayoutStatus.FAILED);
        
        UUID failTransactionId = com.fooddelivery.common.util.DeterministicIdUtils.ledgerId("ledger-service", payoutId, "FAIL");
        LedgerAccountType accountType = "RESTAURANT".equals(payout.getPayeeType()) ? LedgerAccountType.RESTAURANT_PAYABLE : LedgerAccountType.DRIVER_PAYABLE;
        LedgerLeg leg = new LedgerLeg(
                LedgerAccountType.PAYOUT_IN_TRANSIT, com.fooddelivery.common.constants.LedgerAccounts.PAYOUT_IN_TRANSIT,
                accountType, payout.getPayeeId(),
                payout.getAmount(), ChargeCategory.PAYOUT_TRANSFER, "Payout FAIL", adminId.toString()
        );
        LedgerTransactionCommand txReq = new LedgerTransactionCommand(
                failTransactionId, payoutId, "ledger-service", "FAIL", List.of(leg)
        );
        doubleEntryLedgerService.record(txReq);

        payout.setFailureReason(reason);
        payoutRepository.save(payout);

        List<PayoutLine> lines = payoutLineRepository.findByPayoutId(payoutId);
        lines.forEach(l -> l.setActive(false));
        payoutLineRepository.saveAll(lines);
    }

    @Transactional
    public void cancel(UUID payoutId, UUID adminId) {
        Payout payout = payoutRepository.findById(payoutId).orElseThrow();
        if (payout.getStatus() == PayoutStatus.CANCELLED) {
            return;
        }
        stateMachine.transitionTo(payout, PayoutStatus.CANCELLED);
        
        UUID cancelTransactionId = com.fooddelivery.common.util.DeterministicIdUtils.ledgerId("ledger-service", payoutId, "CANCEL");
        LedgerAccountType accountType = "RESTAURANT".equals(payout.getPayeeType()) ? LedgerAccountType.RESTAURANT_PAYABLE : LedgerAccountType.DRIVER_PAYABLE;
        LedgerLeg leg = new LedgerLeg(
                LedgerAccountType.PAYOUT_IN_TRANSIT, com.fooddelivery.common.constants.LedgerAccounts.PAYOUT_IN_TRANSIT,
                accountType, payout.getPayeeId(),
                payout.getAmount(), ChargeCategory.PAYOUT_TRANSFER, "Payout CANCEL", adminId.toString()
        );
        LedgerTransactionCommand txReq = new LedgerTransactionCommand(
                cancelTransactionId, payoutId, "ledger-service", "CANCEL", List.of(leg)
        );
        doubleEntryLedgerService.record(txReq);

        payoutRepository.save(payout);

        List<PayoutLine> lines = payoutLineRepository.findByPayoutId(payoutId);
        lines.forEach(l -> l.setActive(false));
        payoutLineRepository.saveAll(lines);
    }

    @Transactional(readOnly = true)
    public Payout getPayout(UUID payoutId) {
        return payoutRepository.findById(payoutId).orElseThrow(() -> new IllegalArgumentException("Payout not found: " + payoutId));
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<Payout> getPayouts(String payeeType, UUID payeeId, org.springframework.data.domain.Pageable pageable) {
        return payoutRepository.findByPayeeTypeAndPayeeId(payeeType, payeeId, pageable);
    }

    @Transactional(readOnly = true)
    public com.fooddelivery.ledger.dto.PayoutDetailResponse getDetail(UUID payoutId) {
        Payout payout = payoutRepository.findById(payoutId).orElseThrow(() -> new IllegalArgumentException("Payout not found: " + payoutId));
        List<com.fooddelivery.ledger.entity.PayoutLine> lines = payoutLineRepository.findByPayoutId(payoutId);
        return com.fooddelivery.ledger.dto.PayoutDetailResponse.builder()
                .id(payout.getId())
                .payeeType(payout.getPayeeType())
                .payeeId(payout.getPayeeId())
                .payeeDisplayName(payout.getPayeeDisplayName())
                .periodFrom(payout.getPeriodFrom())
                .periodTo(payout.getPeriodTo())
                .amount(payout.getAmount())
                .currency(payout.getCurrency())
                .status(payout.getStatus())
                .beneficiary(readBeneficiary(payout.getBeneficiarySnapshot()))
                .beneficiarySnapshot(payout.getBeneficiarySnapshot())
                .bankReference(payout.getBankReference())
                .failureReason(payout.getFailureReason())
                .createdBy(payout.getCreatedBy())
                .approvedBy(payout.getApprovedBy())
                .paidBy(payout.getPaidBy())
                .ledgerTransactionId(payout.getLedgerTransactionId())
                .settledTransactionId(payout.getSettledTransactionId())
                .createdAt(payout.getCreatedAt())
                .approvedAt(payout.getApprovedAt())
                .paidAt(payout.getPaidAt())
                .lines(lines.stream().map(com.fooddelivery.ledger.mapper.PayoutMapper::toDto).collect(Collectors.toList()))
                .build();
    }

    private BeneficiaryResponse readBeneficiary(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) return null;
        try {
            return objectMapper.readValue(snapshot, BeneficiaryResponse.class);
        } catch (Exception e) {
            log.warn("Unreadable beneficiary snapshot on a payout: {}", e.getMessage());
            return null;
        }
    }

    /**
     * What one payee is owed and when they were last paid. This is what the restaurant and rider
     * summary cards need, and it is served to the SERVICE identity -- those callers used to reach for
     * the admin-only queue of every payee on the platform, which is both a 403 and a data leak.
     */
    @Transactional(readOnly = true)
    public com.fooddelivery.common.dto.ledger.PayeeMoneySummaryDto payeeSummary(String payeeType, UUID payeeId) {
        LedgerAccountType accountType = "RESTAURANT".equals(payeeType)
                ? LedgerAccountType.RESTAURANT_PAYABLE : LedgerAccountType.DRIVER_PAYABLE;

        BigDecimal balance = accountRepository.findByOwnerIdAndOwnerType(payeeId, accountType)
                .map(LedgerAccount::getBalance).orElse(BigDecimal.ZERO);

        BigDecimal inFlight = payoutRepository
                .findByPayeeTypeAndPayeeIdAndStatusIn(payeeType, payeeId, List.of(PayoutStatus.DRAFT, PayoutStatus.APPROVED))
                .stream().map(Payout::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Payout last = payoutRepository
                .findFirstByPayeeTypeAndPayeeIdAndStatusOrderByPaidAtDesc(payeeType, payeeId, PayoutStatus.PAID)
                .orElse(null);

        BeneficiaryResponse beneficiary = null;
        try {
            beneficiary = beneficiaryClient.getBeneficiary(payeeType, payeeId);
        } catch (Exception ex) {
            log.warn("Failed to get beneficiary for {} {}: {}", payeeType, payeeId, ex.getMessage());
        }

        return com.fooddelivery.common.dto.ledger.PayeeMoneySummaryDto.builder()
                .payeeType(payeeType)
                .payeeId(payeeId)
                .unsettledAmount(balance.subtract(inFlight).max(BigDecimal.ZERO))
                .pendingPayoutAmount(inFlight)
                .lastPayout(com.fooddelivery.ledger.mapper.PayoutMapper.toDto(last))
                .beneficiary(beneficiary)
                .build();
    }
}
