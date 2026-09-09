package com.fooddelivery.ledger.service;

import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.exception.LedgerRejectedException;
import com.fooddelivery.common.util.DeterministicIdUtils;
import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.repository.ILedgerEntryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A ledger transaction id must be re-derivable from (producer, referenceId, leg). That is what makes
 * a replay idempotent and a forged id impossible.
 *
 * <p>The check previously tried two hardcoded leg names and then fell back to asking only whether the
 * UUID was version 5, so any v5 id from any namespace was accepted and the guarantee did not hold.
 */
public class LedgerTransactionIdValidationTest {

    private DoubleEntryLedgerService service;
    private ILedgerAccountRepository accountRepo;
    private ILedgerEntryRepository entryRepo;

    private static final String PRODUCER = "customer-application";

    @BeforeEach
    void setUp() {
        accountRepo = mock(ILedgerAccountRepository.class);
        entryRepo = mock(ILedgerEntryRepository.class);
        service = new DoubleEntryLedgerService(accountRepo, entryRepo, mock(EntityManager.class));
    }

    private LedgerTransactionCommand command(UUID txId, UUID reference, String leg) {
        LedgerLeg movement = new LedgerLeg();
        movement.setAmount(new BigDecimal("10.00"));
        movement.setCategory(ChargeCategory.ORDER_TOTAL);
        movement.setFromType(LedgerAccountType.GATEWAY_RECEIVABLE);
        movement.setFromId(UUID.randomUUID());
        movement.setToType(LedgerAccountType.PLATFORM_CLEARING);
        movement.setToId(UUID.randomUUID());
        return new LedgerTransactionCommand(txId, reference, PRODUCER, leg, List.of(movement));
    }

    /** Lets a well-formed command reach the point where it would be written. */
    private void stubAccounts() {
        when(accountRepo.findByOwnerIdAndOwnerType(any(), any())).thenAnswer(inv -> {
            LedgerAccount a = new LedgerAccount();
            a.setId(UUID.randomUUID());
            a.setKind(((LedgerAccountType) inv.getArgument(1)).getKind());
            a.setBalance(BigDecimal.ZERO);
            return Optional.of(a);
        });
        when(accountRepo.findByIdForUpdate(any())).thenAnswer(inv -> {
            LedgerAccount a = new LedgerAccount();
            a.setId(inv.getArgument(0));
            a.setKind(LedgerAccountType.Kind.INTERNAL);
            a.setBalance(BigDecimal.ZERO);
            return Optional.of(a);
        });
    }

    @Test
    void aDerivableIdIsAccepted() {
        stubAccounts();
        UUID reference = UUID.randomUUID();
        UUID txId = DeterministicIdUtils.ledgerId(PRODUCER, reference, "DELIVERED");

        assertDoesNotThrow(() -> service.record(command(txId, reference, "DELIVERED")));
        verify(entryRepo, atLeastOnce()).save(any());
    }

    @Test
    void aV5IdDerivedFromADifferentLegIsRejected() {
        UUID reference = UUID.randomUUID();
        // Version 5, correct namespace, correct producer and reference -- only the leg differs. The old
        // check passed this, which meant one movement's id could be reused for another.
        UUID wrongLeg = DeterministicIdUtils.ledgerId(PRODUCER, reference, "PAYMENT_CAPTURE");

        LedgerRejectedException e = assertThrows(LedgerRejectedException.class,
                () -> service.record(command(wrongLeg, reference, "DELIVERED")));
        assertTrue(e.getMessage().contains("not derivable"), e.getMessage());
        verify(entryRepo, never()).save(any());
    }

    @Test
    void aV5IdDerivedFromADifferentReferenceIsRejected() {
        UUID reference = UUID.randomUUID();
        UUID otherOrder = DeterministicIdUtils.ledgerId(PRODUCER, UUID.randomUUID(), "DELIVERED");

        assertThrows(LedgerRejectedException.class,
                () -> service.record(command(otherOrder, reference, "DELIVERED")));
    }

    @Test
    void aV5IdFromAnotherProducerIsRejected() {
        UUID reference = UUID.randomUUID();
        UUID otherProducer = DeterministicIdUtils.ledgerId("wallet-service", reference, "DELIVERED");

        assertThrows(LedgerRejectedException.class,
                () -> service.record(command(otherProducer, reference, "DELIVERED")));
    }

    @Test
    void aRandomV4IdIsRejected() {
        UUID reference = UUID.randomUUID();
        assertThrows(LedgerRejectedException.class,
                () -> service.record(command(UUID.randomUUID(), reference, "DELIVERED")));
    }

    @Test
    void aCommandWithoutALegIsRejected() {
        UUID reference = UUID.randomUUID();
        UUID txId = DeterministicIdUtils.ledgerId(PRODUCER, reference, "DELIVERED");

        LedgerRejectedException e = assertThrows(LedgerRejectedException.class,
                () -> service.record(command(txId, reference, null)));
        assertTrue(e.getMessage().contains("leg"), e.getMessage());
    }
}
