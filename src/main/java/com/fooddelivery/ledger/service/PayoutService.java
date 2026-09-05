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
        List<LedgerAccountType> payableTypes = List.of(LedgerAccountType.RESTAURANT_PAYABLE, LedgerAccountType.DRIVER_PAYABLE);
        List<LedgerAccount> accounts = accountRepository.findByOwnerTypeInAndBalanceGreaterThan(payableTypes, BigDecimal.ZERO);

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
                // Fetch beneficiary
                BeneficiaryResponse beneficiary = beneficiaryClient.getBeneficiary(payeeType, payeeId);
                String displayName = ownerNameResolver.resolveDisplayName(payeeType, payeeId);
                
                // Get unsettled entries to count lines and find unsettled since
                List<LedgerEntry> unsettledEntries = entryRepository.findUnsettledEntries(acc.getId(), OffsetDateTime.now());
                OffsetDateTime unsettledSince = unsettledEntries.stream()
                        .map(LedgerEntry::getCreatedAt)
                        .min(OffsetDateTime::compareTo)
                        .orElse(null);

                Payout lastPayout = null; // Could query for latest PAID payout

                responses.add(PendingPayoutResponse.builder()
                        .payeeType(payeeType)
                        .payeeId(payeeId)
                        .displayName(displayName)
                        .unsettledAmount(unsettledAmount)
                        .unsettledSince(unsettledSince)
                        .lineCount(unsettledEntries.size())
                        .lastPayout(lastPayout)
                        .beneficiaryStatus(beneficiary)
                        .build());
            }
        }
        return responses;
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

        UUID payoutId = UUID.randomUUID();
        String snapshot;
        try {
            snapshot = objectMapper.writeValueAsString(beneficiary);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize beneficiary", e);
        }

        UUID ledgerTransactionId = UUID.randomUUID();
        LedgerLeg leg = new LedgerLeg(
                accountType, request.getPayeeId(),
                LedgerAccountType.PAYOUT_IN_TRANSIT, UUID.fromString("00000000-0000-0000-0000-000000000000"), // Assuming a generic transit account
                amount, ChargeCategory.PAYOUT_TRANSFER, "Payout CREATE", adminId.toString()
        );
        LedgerTransactionCommand txReq = new LedgerTransactionCommand(
                ledgerTransactionId, payoutId, "ledger-service", List.of(leg)
        );
        doubleEntryLedgerService.record(txReq);

        OffsetDateTime minDate = entries.stream().map(LedgerEntry::getCreatedAt).min(OffsetDateTime::compareTo).orElse(request.getPeriodTo());

        Payout payout = Payout.builder()
                .id(payoutId)
                .payeeType(request.getPayeeType())
                .payeeId(request.getPayeeId())
                .payeeDisplayName(ownerNameResolver.resolveDisplayName(request.getPayeeType(), request.getPayeeId()))
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
        
        UUID settledTransactionId = UUID.randomUUID();
        LedgerLeg leg = new LedgerLeg(
                LedgerAccountType.PAYOUT_IN_TRANSIT, UUID.fromString("00000000-0000-0000-0000-000000000000"),
                LedgerAccountType.BANK, UUID.fromString("00000000-0000-0000-0000-000000000000"),
                payout.getAmount(), ChargeCategory.PAYOUT_TRANSFER, "Payout PAID", adminId.toString()
        );
        LedgerTransactionCommand txReq = new LedgerTransactionCommand(
                settledTransactionId, payoutId, "ledger-service", List.of(leg)
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
        
        UUID failTransactionId = UUID.randomUUID();
        LedgerAccountType accountType = "RESTAURANT".equals(payout.getPayeeType()) ? LedgerAccountType.RESTAURANT_PAYABLE : LedgerAccountType.DRIVER_PAYABLE;
        LedgerLeg leg = new LedgerLeg(
                LedgerAccountType.PAYOUT_IN_TRANSIT, UUID.fromString("00000000-0000-0000-0000-000000000000"),
                accountType, payout.getPayeeId(),
                payout.getAmount(), ChargeCategory.PAYOUT_TRANSFER, "Payout FAIL", adminId.toString()
        );
        LedgerTransactionCommand txReq = new LedgerTransactionCommand(
                failTransactionId, payoutId, "ledger-service", List.of(leg)
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
        
        UUID cancelTransactionId = UUID.randomUUID();
        LedgerAccountType accountType = "RESTAURANT".equals(payout.getPayeeType()) ? LedgerAccountType.RESTAURANT_PAYABLE : LedgerAccountType.DRIVER_PAYABLE;
        LedgerLeg leg = new LedgerLeg(
                LedgerAccountType.PAYOUT_IN_TRANSIT, UUID.fromString("00000000-0000-0000-0000-000000000000"),
                accountType, payout.getPayeeId(),
                payout.getAmount(), ChargeCategory.PAYOUT_TRANSFER, "Payout CANCEL", adminId.toString()
        );
        LedgerTransactionCommand txReq = new LedgerTransactionCommand(
                cancelTransactionId, payoutId, "ledger-service", List.of(leg)
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
}
