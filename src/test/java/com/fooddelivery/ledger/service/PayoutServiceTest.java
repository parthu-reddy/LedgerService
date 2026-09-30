package com.fooddelivery.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.dto.ledger.BeneficiaryResponse;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.common.util.DeterministicIdUtils;
import com.fooddelivery.ledger.client.BeneficiaryClient;
import com.fooddelivery.ledger.dto.CreatePayoutRequest;
import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.entity.LedgerEntry;
import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.entity.PayoutLine;
import com.fooddelivery.ledger.entity.PayoutOperation;
import com.fooddelivery.ledger.entity.PayoutOperationAction;
import com.fooddelivery.ledger.entity.PayoutStatus;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.repository.ILedgerEntryRepository;
import com.fooddelivery.ledger.repository.PayoutLineRepository;
import com.fooddelivery.ledger.repository.PayoutOperationRepository;
import com.fooddelivery.ledger.repository.PayoutRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The payout lifecycle.
 *
 * <p>This class was once a single test method with an empty body. That is the direct reason every
 * payout threw at runtime: the ledger transaction ids were v4 while the ledger
 * only accepts an id it can re-derive from (producer, reference, leg), and nothing noticed. The
 * derivability of each id is therefore asserted here on every transition.
 */
public class PayoutServiceTest {

    @Mock private PayoutRepository payoutRepository;
    @Mock private PayoutLineRepository payoutLineRepository;
    @Mock private PayoutOperationRepository payoutOperationRepository;
    @Mock private ILedgerAccountRepository accountRepository;
    @Mock private ILedgerEntryRepository entryRepository;
    @Mock private BeneficiaryClient beneficiaryClient;
    @Mock private OwnerNameResolver ownerNameResolver;
    @Mock private PayoutStateMachine stateMachine;
    @Mock private DoubleEntryLedgerService doubleEntryLedgerService;

    private PayoutService payoutService;

    private final UUID payeeId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID adminId = UUID.randomUUID();
    private final UUID otherAdminId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        payoutService = new PayoutService(payoutRepository, payoutLineRepository, payoutOperationRepository, accountRepository,
                entryRepository, beneficiaryClient, ownerNameResolver, stateMachine,
                doubleEntryLedgerService, new ObjectMapper());
        ReflectionTestUtils.setField(payoutService, "fourEyesEnabled", true);
        when(payoutOperationRepository.insertIfAbsent(
                any(UUID.class), any(UUID.class), anyString(), anyString(), anyString(), any(UUID.class),
                anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class),
                nullable(UUID.class))).thenReturn(1);
    }

    private CreatePayoutRequest request() {
        return CreatePayoutRequest.builder()
                .payeeType("RESTAURANT").payeeId(payeeId)
                .periodTo(Instant.parse("2026-09-07T00:00:00Z")).build();
    }

    private LedgerEntry entry(String amount, TransactionDirection direction) {
        return LedgerEntry.builder()
                .id(UUID.randomUUID()).accountId(accountId).referenceId(UUID.randomUUID())
                .amount(new BigDecimal(amount)).direction(direction)
                .category(com.fooddelivery.common.enums.ChargeCategory.FOOD_COST)
                .createdAt(Instant.parse("2026-09-01T00:00:00Z")).build();
    }

    private void payableAccountExists() {
        when(accountRepository.findByOwnerIdAndOwnerTypeForUpdate(payeeId, LedgerAccountType.RESTAURANT_PAYABLE))
                .thenReturn(Optional.of(LedgerAccount.builder().id(accountId).ownerId(payeeId)
                        .ownerType(LedgerAccountType.RESTAURANT_PAYABLE).balance(new BigDecimal("500.00")).build()));
    }

    private void payeeIsNamed() {
        when(ownerNameResolver.resolveDisplayName("RESTAURANT", payeeId))
                .thenReturn(new OwnerNameResolver.ResolvedName("Kanti Sweets", true));
    }

    private void beneficiaryIsVerified() {
        when(beneficiaryClient.getBeneficiary("RESTAURANT", payeeId))
                .thenReturn(BeneficiaryResponse.builder().verified(true).accountNumberMasked("XXXX1234").build());
    }

    private LedgerTransactionCommand recordedCommand() {
        ArgumentCaptor<LedgerTransactionCommand> captor = ArgumentCaptor.forClass(LedgerTransactionCommand.class);
        verify(doubleEntryLedgerService).record(captor.capture());
        return captor.getValue();
    }

    // --- create -------------------------------------------------------------------------------

    @Test
    void createPaysTheUnsettledLinesAndSnapshotsTheBeneficiary() {
        payableAccountExists();
        payeeIsNamed();
        beneficiaryIsVerified();
        when(payoutRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(payoutRepository.findByPayeeTypeAndPayeeIdAndStatusIn(any(), any(), any())).thenReturn(List.of());
        when(entryRepository.findUnsettledEntries(any(), any())).thenReturn(List.of(
                entry("300.00", TransactionDirection.CREDIT),
                entry("100.00", TransactionDirection.CREDIT),
                entry("50.00", TransactionDirection.DEBIT)));

        Payout payout = payoutService.create(request(), "key-1", adminId);

        // credits less debits, not the raw account balance
        assertEquals(0, new BigDecimal("350.00").compareTo(payout.getAmount()));
        assertEquals(PayoutStatus.DRAFT, payout.getStatus());
        assertEquals("Kanti Sweets", payout.getPayeeDisplayName());
        assertTrue(payout.getBeneficiarySnapshot().contains("XXXX1234"));

        ArgumentCaptor<List<PayoutLine>> lines = ArgumentCaptor.forClass(List.class);
        verify(payoutLineRepository).saveAll(lines.capture());
        assertEquals(3, lines.getValue().size(), "every line that was settled must be snapshotted");
    }

    /** The defect that shipped: a transaction id the ledger cannot re-derive is rejected outright. */
    @Test
    void createBooksALedgerIdTheLedgerCanReDerive() {
        payableAccountExists();
        payeeIsNamed();
        beneficiaryIsVerified();
        when(payoutRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(payoutRepository.findByPayeeTypeAndPayeeIdAndStatusIn(any(), any(), any())).thenReturn(List.of());
        when(entryRepository.findUnsettledEntries(any(), any())).thenReturn(List.of(entry("350.00", TransactionDirection.CREDIT)));

        Payout payout = payoutService.create(request(), "key-1", adminId);
        LedgerTransactionCommand cmd = recordedCommand();

        assertEquals("CREATE", cmd.getLeg());
        assertEquals(payout.getId(), cmd.getReferenceId());
        assertEquals(5, cmd.getTransactionId().version(), "the ledger rejects anything but a v5 id");
        assertTrue(DeterministicIdUtils.isLedgerId(cmd.getTransactionId(), "ledger-service",
                payout.getId().toString(), "CREATE"));

        LedgerLeg leg = cmd.getLegs().get(0);
        assertEquals(LedgerAccountType.RESTAURANT_PAYABLE, leg.getFromType());
        assertEquals(LedgerAccountType.PAYOUT_IN_TRANSIT, leg.getToType());
        assertEquals(0, new BigDecimal("350.00").compareTo(leg.getAmount()));
    }

    @Test
    void createIsIdempotentOnTheKeyAndBooksNothingTwice() {
        Payout existing = Payout.builder().id(UUID.randomUUID()).amount(new BigDecimal("350.00")).build();
        when(payoutRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        Payout result = payoutService.create(request(), "key-1", adminId);

        assertEquals(existing.getId(), result.getId());
        verify(doubleEntryLedgerService, never()).record(any());
        verify(payoutRepository, never()).save(any());
    }

    @Test
    void createRefusesWhileAnotherPayoutIsStillPending() {
        when(payoutRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(payoutRepository.findByPayeeTypeAndPayeeIdAndStatusIn(any(), any(), any()))
                .thenReturn(List.of(Payout.builder().status(PayoutStatus.DRAFT).build()));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> payoutService.create(request(), "key-2", adminId));
        assertEquals("PAYOUT_ALREADY_PENDING", e.getMessage());
        verify(doubleEntryLedgerService, never()).record(any());
    }

    @Test
    void createRefusesAnUnverifiedBeneficiaryUnlessForced() {
        payableAccountExists();
        when(payoutRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(payoutRepository.findByPayeeTypeAndPayeeIdAndStatusIn(any(), any(), any())).thenReturn(List.of());
        when(entryRepository.findUnsettledEntries(any(), any())).thenReturn(List.of(entry("350.00", TransactionDirection.CREDIT)));
        when(beneficiaryClient.getBeneficiary("RESTAURANT", payeeId))
                .thenReturn(BeneficiaryResponse.builder().verified(false).build());

        assertThrows(IllegalStateException.class, () -> payoutService.create(request(), "key-3", adminId));
        verify(doubleEntryLedgerService, never()).record(any());
    }

    /**
     * payouts.payee_display_name is the audit record of who was paid. A stand-in assembled from the
     * id is not a name, and it used to be persisted silently.
     */
    @Test
    void createRefusesWhenThePayeeCannotBeNamed() {
        payableAccountExists();
        beneficiaryIsVerified();
        when(payoutRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(payoutRepository.findByPayeeTypeAndPayeeIdAndStatusIn(any(), any(), any())).thenReturn(List.of());
        when(entryRepository.findUnsettledEntries(any(), any())).thenReturn(List.of(entry("350.00", TransactionDirection.CREDIT)));
        when(ownerNameResolver.resolveDisplayName("RESTAURANT", payeeId))
                .thenReturn(OwnerNameResolver.ResolvedName.unresolved("RESTAURANT", payeeId));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> payoutService.create(request(), "key-4", adminId));
        assertTrue(e.getMessage().startsWith("PAYEE_NAME_UNRESOLVED"), e.getMessage());
        verify(payoutRepository, never()).save(any());
    }

    @Test
    void createRefusesWhenThereIsNothingToPay() {
        payableAccountExists();
        when(payoutRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(payoutRepository.findByPayeeTypeAndPayeeIdAndStatusIn(any(), any(), any())).thenReturn(List.of());
        when(entryRepository.findUnsettledEntries(any(), any())).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> payoutService.create(request(), "key-5", adminId));
    }

    // --- approve ------------------------------------------------------------------------------

    @Test
    void approveRefusesTheAdministratorWhoRaisedIt() {
        Payout payout = Payout.builder().id(UUID.randomUUID()).status(PayoutStatus.DRAFT).createdBy(adminId).build();
        when(payoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> payoutService.approve(payout.getId(), adminId, "approve-own"));
        assertTrue(e.getMessage().contains("Four-eyes"), e.getMessage());
        verify(payoutRepository, never()).save(any());
        verify(payoutOperationRepository, never()).insertIfAbsent(
                any(), any(), anyString(), anyString(), anyString(), any(), anyString(), anyString(),
                anyString(), nullable(String.class), nullable(String.class), nullable(UUID.class));
    }

    @Test
    void approveAcceptsASecondAdministrator() {
        Payout payout = Payout.builder().id(UUID.randomUUID()).status(PayoutStatus.DRAFT).createdBy(adminId).build();
        when(payoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));

        payoutService.approve(payout.getId(), otherAdminId, "approve-second-admin");

        verify(payoutOperationRepository).insertIfAbsent(
                any(UUID.class), eq(payout.getId()), eq(PayoutOperationAction.APPROVE.name()),
                eq("approve-second-admin"), anyString(), eq(otherAdminId), eq(PayoutStatus.DRAFT.name()),
                eq(PayoutStatus.APPROVED.name()), eq("APPLIED"), isNull(), isNull(), isNull());
        verify(stateMachine).transitionTo(payout, PayoutStatus.APPROVED);
        assertEquals(otherAdminId, payout.getApprovedBy());
        assertNotNull(payout.getApprovedAt());
    }

    // --- mark paid / fail / cancel --------------------------------------------------------------

    @Test
    void markPaidMovesTheMoneyOutOfTransitAndRecordsTheBankReference() {
        Payout payout = Payout.builder().id(UUID.randomUUID()).status(PayoutStatus.APPROVED)
                .payeeType("RESTAURANT").payeeId(payeeId).amount(new BigDecimal("350.00")).build();
        when(payoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));

        payoutService.markPaid(payout.getId(), " UTR-99 ", adminId, "mark-paid-1");

        LedgerTransactionCommand cmd = recordedCommand();
        assertEquals("PAID", cmd.getLeg());
        assertTrue(DeterministicIdUtils.isLedgerId(cmd.getTransactionId(), "ledger-service",
                payout.getId().toString(), "PAID"));
        assertEquals(LedgerAccountType.PAYOUT_IN_TRANSIT, cmd.getLegs().get(0).getFromType());
        assertEquals(LedgerAccountType.BANK, cmd.getLegs().get(0).getToType());
        assertEquals("UTR-99", payout.getBankReference());
        assertEquals(cmd.getTransactionId(), payout.getSettledTransactionId());
        assertNotNull(payout.getPaidAt());
    }

    /** Failing a payout must put the money back where it came from and free the lines. */
    @Test
    void failReversesToThePayableAndFreesTheLines() {
        Payout payout = Payout.builder().id(UUID.randomUUID()).status(PayoutStatus.APPROVED)
                .payeeType("RESTAURANT").payeeId(payeeId).amount(new BigDecimal("350.00")).build();
        when(payoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));
        PayoutLine line = PayoutLine.builder().id(UUID.randomUUID()).payoutId(payout.getId()).active(true).build();
        when(payoutLineRepository.findByPayoutId(payout.getId())).thenReturn(List.of(line));

        payoutService.fail(payout.getId(), " bank rejected the account ", adminId, "fail-1");

        LedgerTransactionCommand cmd = recordedCommand();
        assertEquals("FAIL", cmd.getLeg());
        assertTrue(DeterministicIdUtils.isLedgerId(cmd.getTransactionId(), "ledger-service",
                payout.getId().toString(), "FAIL"));
        assertEquals(LedgerAccountType.PAYOUT_IN_TRANSIT, cmd.getLegs().get(0).getFromType());
        assertEquals(LedgerAccountType.RESTAURANT_PAYABLE, cmd.getLegs().get(0).getToType());
        assertEquals(payeeId, cmd.getLegs().get(0).getToId());
        assertEquals("bank rejected the account", payout.getFailureReason());
        assertFalse(line.isActive(), "a failed payout must release its lines for the next attempt");
    }

    @Test
    void cancelReversesToThePayableAndFreesTheLines() {
        Payout payout = Payout.builder().id(UUID.randomUUID()).status(PayoutStatus.DRAFT)
                .payeeType("RESTAURANT").payeeId(payeeId).amount(new BigDecimal("350.00")).build();
        when(payoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));
        PayoutLine line = PayoutLine.builder().id(UUID.randomUUID()).payoutId(payout.getId()).active(true).build();
        when(payoutLineRepository.findByPayoutId(payout.getId())).thenReturn(List.of(line));

        payoutService.cancel(payout.getId(), adminId, "cancel-1");

        LedgerTransactionCommand cmd = recordedCommand();
        assertEquals("CANCEL", cmd.getLeg());
        assertTrue(DeterministicIdUtils.isLedgerId(cmd.getTransactionId(), "ledger-service",
                payout.getId().toString(), "CANCEL"));
        verify(stateMachine).transitionTo(payout, PayoutStatus.CANCELLED);
        assertFalse(line.isActive());
    }

    @Test
    void aNewMarkPaidKeyCannotReopenAnAlreadyPaidPayout() {
        Payout payout = Payout.builder().id(UUID.randomUUID()).status(PayoutStatus.PAID).build();
        when(payoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> payoutService.markPaid(payout.getId(), "UTR-99", adminId, "different-key"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(doubleEntryLedgerService, never()).record(any());
        verify(payoutOperationRepository, never()).insertIfAbsent(
                any(), any(), anyString(), anyString(), anyString(), any(), anyString(), anyString(),
                anyString(), nullable(String.class), nullable(String.class), nullable(UUID.class));
    }

    @Test
    void markPaidClaimsAnImmutableAuditRecordBeforeItPostsTheLedgerMovement() {
        Payout payout = Payout.builder().id(UUID.randomUUID()).status(PayoutStatus.APPROVED)
                .payeeType("RESTAURANT").payeeId(payeeId).amount(new BigDecimal("350.00")).build();
        when(payoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));
        when(payoutOperationRepository.findByIdempotencyKey("paid-audit-key")).thenReturn(Optional.empty());

        payoutService.markPaid(payout.getId(), "UTR-100", adminId, "paid-audit-key");

        ArgumentCaptor<String> requestHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> ledgerTransactionId = ArgumentCaptor.forClass(UUID.class);
        InOrder ordered = inOrder(payoutOperationRepository, doubleEntryLedgerService);
        ordered.verify(payoutOperationRepository).insertIfAbsent(
                any(UUID.class), eq(payout.getId()), eq(PayoutOperationAction.MARK_PAID.name()),
                eq("paid-audit-key"), requestHash.capture(), eq(adminId), eq(PayoutStatus.APPROVED.name()),
                eq(PayoutStatus.PAID.name()), eq("APPLIED"), eq("UTR-100"), isNull(), ledgerTransactionId.capture());
        ordered.verify(doubleEntryLedgerService).record(any(LedgerTransactionCommand.class));

        assertNotNull(ledgerTransactionId.getValue());
        assertEquals(64, requestHash.getValue().length());
    }

    @Test
    void anExactPayoutActionReplayDoesNotLockOrPostAnotherLedgerMovement() {
        Payout payout = Payout.builder().id(UUID.randomUUID()).status(PayoutStatus.APPROVED)
                .payeeType("RESTAURANT").payeeId(payeeId).amount(new BigDecimal("350.00")).build();
        when(payoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));
        when(payoutOperationRepository.findByIdempotencyKey("replay-key")).thenReturn(Optional.empty());

        payoutService.markPaid(payout.getId(), "UTR-101", adminId, "replay-key");
        ArgumentCaptor<String> requestHash = ArgumentCaptor.forClass(String.class);
        verify(payoutOperationRepository).insertIfAbsent(
                any(UUID.class), any(UUID.class), anyString(), eq("replay-key"), requestHash.capture(), any(UUID.class),
                anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class), nullable(UUID.class));

        clearInvocations(payoutRepository, payoutOperationRepository, payoutLineRepository,
                doubleEntryLedgerService, stateMachine);
        when(payoutOperationRepository.findByIdempotencyKey("replay-key"))
                .thenReturn(Optional.of(PayoutOperation.builder()
                        .payoutId(payout.getId())
                        .action(PayoutOperationAction.MARK_PAID)
                        .actorId(adminId)
                        .requestHash(requestHash.getValue())
                        .build()));

        payoutService.markPaid(payout.getId(), "UTR-101", adminId, "replay-key");

        verify(payoutOperationRepository).findByIdempotencyKey("replay-key");
        verify(payoutRepository, never()).findByIdForUpdate(any());
        verify(payoutOperationRepository, never()).insertIfAbsent(
                any(), any(), anyString(), anyString(), anyString(), any(), anyString(), anyString(),
                anyString(), nullable(String.class), nullable(String.class), nullable(UUID.class));
        verifyNoInteractions(doubleEntryLedgerService);
        verifyNoInteractions(payoutLineRepository);
    }

    @Test
    void reusingAnActionKeyWithChangedSettlementInputIsAConflict() {
        Payout payout = Payout.builder().id(UUID.randomUUID()).status(PayoutStatus.APPROVED)
                .payeeType("RESTAURANT").payeeId(payeeId).amount(new BigDecimal("350.00")).build();
        when(payoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));
        when(payoutOperationRepository.findByIdempotencyKey("changed-input-key")).thenReturn(Optional.empty());

        payoutService.markPaid(payout.getId(), "UTR-102", adminId, "changed-input-key");
        ArgumentCaptor<String> requestHash = ArgumentCaptor.forClass(String.class);
        verify(payoutOperationRepository).insertIfAbsent(
                any(UUID.class), any(UUID.class), anyString(), eq("changed-input-key"), requestHash.capture(), any(UUID.class),
                anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class), nullable(UUID.class));

        clearInvocations(payoutRepository, payoutOperationRepository, payoutLineRepository,
                doubleEntryLedgerService, stateMachine);
        when(payoutOperationRepository.findByIdempotencyKey("changed-input-key"))
                .thenReturn(Optional.of(PayoutOperation.builder()
                        .payoutId(payout.getId())
                        .action(PayoutOperationAction.MARK_PAID)
                        .actorId(adminId)
                        .requestHash(requestHash.getValue())
                        .build()));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> payoutService.markPaid(payout.getId(), "UTR-103", adminId, "changed-input-key"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(payoutRepository, never()).findByIdForUpdate(any());
        verify(payoutOperationRepository, never()).insertIfAbsent(
                any(), any(), anyString(), anyString(), anyString(), any(), anyString(), anyString(),
                anyString(), nullable(String.class), nullable(String.class), nullable(UUID.class));
        verifyNoInteractions(doubleEntryLedgerService);
    }

    @Test
    void reusingAnOperationKeyForAnotherPayoutActionIsAConflict() {
        Payout payout = Payout.builder().id(UUID.randomUUID()).status(PayoutStatus.APPROVED)
                .payeeType("RESTAURANT").payeeId(payeeId).amount(new BigDecimal("350.00")).build();
        when(payoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));
        when(payoutOperationRepository.findByIdempotencyKey("cross-action-key")).thenReturn(Optional.empty());

        payoutService.markPaid(payout.getId(), "UTR-104", adminId, "cross-action-key");
        ArgumentCaptor<String> requestHash = ArgumentCaptor.forClass(String.class);
        verify(payoutOperationRepository).insertIfAbsent(
                any(UUID.class), any(UUID.class), anyString(), eq("cross-action-key"), requestHash.capture(), any(UUID.class),
                anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class), nullable(UUID.class));

        clearInvocations(payoutRepository, payoutOperationRepository, payoutLineRepository,
                doubleEntryLedgerService, stateMachine);
        when(payoutOperationRepository.findByIdempotencyKey("cross-action-key"))
                .thenReturn(Optional.of(PayoutOperation.builder()
                        .payoutId(payout.getId())
                        .action(PayoutOperationAction.MARK_PAID)
                        .actorId(adminId)
                        .requestHash(requestHash.getValue())
                        .build()));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> payoutService.fail(payout.getId(), "bank rejected the account", adminId, "cross-action-key"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(payoutRepository, never()).findByIdForUpdate(any());
        verify(payoutOperationRepository, never()).insertIfAbsent(
                any(), any(), anyString(), anyString(), anyString(), any(), anyString(), anyString(),
                anyString(), nullable(String.class), nullable(String.class), nullable(UUID.class));
        verifyNoInteractions(doubleEntryLedgerService);
    }

    @Test
    void aCrossPayoutKeyClaimRaceReturnsConflictBeforeAnyLedgerMovement() {
        Payout payout = Payout.builder().id(UUID.randomUUID()).status(PayoutStatus.APPROVED)
                .payeeType("RESTAURANT").payeeId(payeeId).amount(new BigDecimal("350.00")).build();
        PayoutOperation winningOperation = PayoutOperation.builder()
                .payoutId(UUID.randomUUID())
                .action(PayoutOperationAction.MARK_PAID)
                .actorId(adminId)
                .requestHash("a".repeat(64))
                .build();
        when(payoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));
        when(payoutOperationRepository.findByIdempotencyKey("cross-payout-key"))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.of(winningOperation));
        when(payoutOperationRepository.insertIfAbsent(
                any(UUID.class), any(UUID.class), anyString(), anyString(), anyString(), any(UUID.class),
                anyString(), anyString(), anyString(), nullable(String.class), nullable(String.class),
                nullable(UUID.class))).thenReturn(0);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> payoutService.markPaid(payout.getId(), "UTR-105", adminId, "cross-payout-key"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(payoutOperationRepository, times(3)).findByIdempotencyKey("cross-payout-key");
        verifyNoInteractions(doubleEntryLedgerService);
    }

    @Test
    void invalidSettlementInputsAreRejectedBeforeAnyPayoutOrLedgerAccess() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> payoutService.markPaid(UUID.randomUUID(), null, adminId, "valid-key")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> payoutService.markPaid(UUID.randomUUID(), "   ", adminId, "valid-key")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> payoutService.markPaid(UUID.randomUUID(), "UTR 123", adminId, "valid-key")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> payoutService.markPaid(UUID.randomUUID(), "A".repeat(129), adminId, "valid-key")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> payoutService.fail(UUID.randomUUID(), null, adminId, "valid-key")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> payoutService.fail(UUID.randomUUID(), " no ", adminId, "valid-key")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> payoutService.fail(UUID.randomUUID(), "reason\nwith control", adminId, "valid-key")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> payoutService.cancel(UUID.randomUUID(), adminId, "  ")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> payoutService.approve(null, adminId, "valid-key")),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> payoutService.approve(UUID.randomUUID(), null, "valid-key"))
        );

        verifyNoInteractions(payoutRepository, payoutOperationRepository, payoutLineRepository,
                doubleEntryLedgerService, stateMachine);
    }

    @Test
    void aCompetingStateTransitionReturnsConflictAfterThePayoutLock() {
        Payout payout = Payout.builder().id(UUID.randomUUID()).status(PayoutStatus.PAID)
                .payeeType("RESTAURANT").payeeId(payeeId).amount(new BigDecimal("350.00")).build();
        when(payoutRepository.findByIdForUpdate(payout.getId())).thenReturn(Optional.of(payout));
        doThrow(new IllegalStateException("Can only fail DRAFT or APPROVED payouts"))
                .when(stateMachine).validateTransition(PayoutStatus.PAID, PayoutStatus.FAILED);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> payoutService.fail(payout.getId(), "bank returned an error", adminId, "competing-state-key"));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(payoutOperationRepository, never()).insertIfAbsent(
                any(), any(), anyString(), anyString(), anyString(), any(), anyString(), anyString(),
                anyString(), nullable(String.class), nullable(String.class), nullable(UUID.class));
        verifyNoInteractions(doubleEntryLedgerService);
    }

    // --- reads --------------------------------------------------------------------------------

    @Test
    void testGetPayoutSuccess() {
        UUID payoutId = UUID.randomUUID();
        when(payoutRepository.findById(payoutId)).thenReturn(
                Optional.of(Payout.builder().id(payoutId).amount(new BigDecimal("100.00")).build()));

        assertEquals(new BigDecimal("100.00"), payoutService.getPayout(payoutId).getAmount());
    }

    @Test
    void testGetPayoutNotFound() {
        UUID payoutId = UUID.randomUUID();
        when(payoutRepository.findById(payoutId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> payoutService.getPayout(payoutId));
    }
}
