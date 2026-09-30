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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final PayoutLineRepository payoutLineRepository;
    private final PayoutOperationRepository payoutOperationRepository;
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
                
                List<LedgerEntry> unsettledEntries = entryRepository.findUnsettledEntries(acc.getId(), Instant.now());
                Instant unsettledSince = unsettledEntries.stream()
                        .map(LedgerEntry::getCreatedAt)
                        .min(Instant::compareTo)
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

        Instant minDate = entries.stream().map(LedgerEntry::getCreatedAt).min(Instant::compareTo).orElse(request.getPeriodTo());

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

    /**
     * Approving, settling, failing, and cancelling a payout are financial operations. Each method
     * locks the payout first, claims an immutable idempotency/audit record, and only then posts a
     * ledger movement. A competing action cannot see the same pre-transition state.
     */
    @Transactional
    public void approve(UUID payoutId, UUID adminId, String idempotencyKey) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = requestHash(payoutId, PayoutOperationAction.APPROVE, adminId, null, null);
        Optional<Payout> claimedPayout = lockAndClaimOperation(
                payoutId, PayoutOperationAction.APPROVE, PayoutStatus.APPROVED,
                adminId, key, requestHash, null, null, null);
        if (claimedPayout.isEmpty()) {
            return;
        }
        Payout payout = claimedPayout.get();

        stateMachine.transitionTo(payout, PayoutStatus.APPROVED);
        payout.setApprovedBy(adminId);
        payout.setApprovedAt(Instant.now());
        payoutRepository.save(payout);
    }

    @Transactional
    public void markPaid(UUID payoutId, String bankReference, UUID adminId, String idempotencyKey) {
        String normalizedBankReference = normalizeBankReference(bankReference);
        String key = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = requestHash(payoutId, PayoutOperationAction.MARK_PAID, adminId, normalizedBankReference, null);
        UUID settledTransactionId = com.fooddelivery.common.util.DeterministicIdUtils.ledgerId("ledger-service", payoutId, "PAID");
        Optional<Payout> claimedPayout = lockAndClaimOperation(
                payoutId, PayoutOperationAction.MARK_PAID, PayoutStatus.PAID,
                adminId, key, requestHash, normalizedBankReference, null, settledTransactionId);
        if (claimedPayout.isEmpty()) {
            return;
        }
        Payout payout = claimedPayout.get();

        stateMachine.transitionTo(payout, PayoutStatus.PAID);
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
        payout.setPaidAt(Instant.now());
        payout.setBankReference(normalizedBankReference);
        payout.setSettledTransactionId(settledTransactionId);
        payoutRepository.save(payout);
    }

    @Transactional
    public void fail(UUID payoutId, String reason, UUID adminId, String idempotencyKey) {
        String normalizedReason = normalizeFailureReason(reason);
        String key = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = requestHash(payoutId, PayoutOperationAction.FAIL, adminId, null, normalizedReason);
        UUID failTransactionId = com.fooddelivery.common.util.DeterministicIdUtils.ledgerId("ledger-service", payoutId, "FAIL");
        Optional<Payout> claimedPayout = lockAndClaimOperation(
                payoutId, PayoutOperationAction.FAIL, PayoutStatus.FAILED,
                adminId, key, requestHash, null, normalizedReason, failTransactionId);
        if (claimedPayout.isEmpty()) {
            return;
        }
        Payout payout = claimedPayout.get();

        stateMachine.transitionTo(payout, PayoutStatus.FAILED);
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

        payout.setFailureReason(normalizedReason);
        payoutRepository.save(payout);
        releasePayoutLines(payoutId);
    }

    @Transactional
    public void cancel(UUID payoutId, UUID adminId, String idempotencyKey) {
        String key = normalizeIdempotencyKey(idempotencyKey);
        String requestHash = requestHash(payoutId, PayoutOperationAction.CANCEL, adminId, null, null);
        UUID cancelTransactionId = com.fooddelivery.common.util.DeterministicIdUtils.ledgerId("ledger-service", payoutId, "CANCEL");
        Optional<Payout> claimedPayout = lockAndClaimOperation(
                payoutId, PayoutOperationAction.CANCEL, PayoutStatus.CANCELLED,
                adminId, key, requestHash, null, null, cancelTransactionId);
        if (claimedPayout.isEmpty()) {
            return;
        }
        Payout payout = claimedPayout.get();

        stateMachine.transitionTo(payout, PayoutStatus.CANCELLED);
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
        releasePayoutLines(payoutId);
    }

    private Optional<Payout> lockAndClaimOperation(UUID payoutId,
                                                    PayoutOperationAction action,
                                                    PayoutStatus targetStatus,
                                                    UUID adminId,
                                                    String idempotencyKey,
                                                    String requestHash,
                                                    String bankReference,
                                                    String failureReason,
                                                    UUID ledgerTransactionId) {
        // Fast deterministic replay. The locked recheck below closes the window where a request
        // starts before another transaction commits its operation record.
        Optional<PayoutOperation> existing = payoutOperationRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            assertMatchingReplay(existing.get(), payoutId, action, adminId, requestHash);
            return Optional.empty();
        }

        Payout payout = payoutRepository.findByIdForUpdate(payoutId)
                .orElseThrow(() -> new IllegalArgumentException("Payout not found: " + payoutId));

        existing = payoutOperationRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            assertMatchingReplay(existing.get(), payoutId, action, adminId, requestHash);
            return Optional.empty();
        }

        if (payout.getStatus() == targetStatus) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payout is already " + targetStatus + "; retry the original operation with its idempotency key");
        }
        try {
            stateMachine.validateTransition(payout.getStatus(), targetStatus);
        } catch (IllegalStateException invalidTransition) {
            // A request that waited on the payout lock is racing a completed action. Expose that as
            // a conflict instead of a generic validation error, so the operator refreshes the
            // payout rather than retrying a now-incompatible financial movement.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Payout state changed before this operation could be applied", invalidTransition);
        }

        if (action == PayoutOperationAction.APPROVE && fourEyesEnabled && adminId.equals(payout.getCreatedBy())) {
            throw new IllegalStateException("Four-eyes principle: cannot approve own payout");
        }

        // Claim the key before a ledger movement. A unique-constraint exception would abort this
        // PostgreSQL transaction, making a replay lookup impossible; ON CONFLICT DO NOTHING instead
        // leaves it usable when a different payout concurrently tried the same key.
        int inserted = payoutOperationRepository.insertIfAbsent(
                UUID.randomUUID(), payoutId, action.name(), idempotencyKey, requestHash, adminId,
                payout.getStatus().name(), targetStatus.name(), "APPLIED", bankReference, failureReason,
                ledgerTransactionId);
        if (inserted == 0) {
            PayoutOperation winningOperation = payoutOperationRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Payout operation key claim was lost without a persisted operation"));
            assertMatchingReplay(winningOperation, payoutId, action, adminId, requestHash);
            return Optional.empty();
        }
        if (inserted != 1) {
            throw new IllegalStateException("Unexpected payout operation claim result: " + inserted);
        }
        return Optional.of(payout);
    }

    private void assertMatchingReplay(PayoutOperation operation,
                                      UUID payoutId,
                                      PayoutOperationAction action,
                                      UUID adminId,
                                      String requestHash) {
        if (!Objects.equals(operation.getPayoutId(), payoutId)
                || operation.getAction() != action
                || !Objects.equals(operation.getActorId(), adminId)
                || !Objects.equals(operation.getRequestHash(), requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency key was already used for a different payout operation");
        }
    }

    private void releasePayoutLines(UUID payoutId) {
        List<PayoutLine> lines = payoutLineRepository.findByPayoutId(payoutId);
        lines.forEach(l -> l.setActive(false));
        payoutLineRepository.saveAll(lines);
    }

    private static String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank() || !value.equals(value.strip()) || value.length() > 255
                || containsControlCharacter(value)) {
            throw new IllegalArgumentException("PAYOUT_IDEMPOTENCY_KEY_INVALID");
        }
        return value;
    }

    private static String normalizeBankReference(String value) {
        String normalized = normalizeRequiredText(value, "PAYOUT_BANK_REFERENCE", 3, 128);
        if (!normalized.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")) {
            throw new IllegalArgumentException("PAYOUT_BANK_REFERENCE_INVALID");
        }
        return normalized;
    }

    private static String normalizeFailureReason(String value) {
        return normalizeRequiredText(value, "PAYOUT_FAILURE_REASON", 5, 1000);
    }

    private static String normalizeRequiredText(String value, String field, int minLength, int maxLength) {
        if (value == null) {
            throw new IllegalArgumentException(field + "_REQUIRED");
        }
        String normalized = value.strip();
        int length = normalized.codePointCount(0, normalized.length());
        if (length < minLength || normalized.length() > maxLength || containsControlCharacter(normalized)) {
            throw new IllegalArgumentException(field + "_INVALID");
        }
        return normalized;
    }

    private static boolean containsControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private static String requestHash(UUID payoutId,
                                      PayoutOperationAction action,
                                      UUID adminId,
                                      String bankReference,
                                      String failureReason) {
        if (payoutId == null) {
            throw new IllegalArgumentException("PAYOUT_ID_REQUIRED");
        }
        if (adminId == null) {
            throw new IllegalArgumentException("PAYOUT_ACTOR_REQUIRED");
        }
        String canonical = action.name() + "\n" + payoutId + "\n" + adminId + "\n"
                + Objects.toString(bankReference, "") + "\n" + Objects.toString(failureReason, "");
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                hex.append(String.format(Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
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
