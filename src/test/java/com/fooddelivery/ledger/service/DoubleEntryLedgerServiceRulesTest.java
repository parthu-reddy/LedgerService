package com.fooddelivery.ledger.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.repository.ILedgerEntryRepository;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.exception.LedgerRejectedException;
import com.fooddelivery.common.util.DeterministicIdUtils;
import com.fooddelivery.ledger.entity.LedgerAccount;
import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.Collections;
import java.util.Optional;

public class DoubleEntryLedgerServiceRulesTest {

    private DoubleEntryLedgerService service;
    private ILedgerAccountRepository accountRepo;
    private ILedgerEntryRepository entryRepo;
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        accountRepo = mock(ILedgerAccountRepository.class);
        entryRepo = mock(ILedgerEntryRepository.class);
        entityManager = mock(EntityManager.class);
        service = new DoubleEntryLedgerService(accountRepo, entryRepo, entityManager);
    }

    @Test
    void testInvalidUUIDVersion_ThrowsException() {
        LedgerTransactionCommand cmd = new LedgerTransactionCommand();
        // UUID v4 instead of v5
        cmd.setTransactionId(UUID.randomUUID());
        cmd.setProducer("TEST");
        cmd.setReferenceId(UUID.randomUUID());
        cmd.setLeg("1");
        
        LedgerRejectedException ex = assertThrows(LedgerRejectedException.class, () -> service.record(cmd));
        assertTrue(ex.getMessage().contains("not derivable"), ex.getMessage());
    }

    @Test
    void testNegativeAmount_ThrowsException() {
        LedgerTransactionCommand cmd = new LedgerTransactionCommand();
        // create a UUID v5
        cmd.setProducer("TEST");
        cmd.setReferenceId(UUID.randomUUID());
        cmd.setLeg("1");
        cmd.setTransactionId(DeterministicIdUtils.ledgerId("TEST", cmd.getReferenceId(), "1"));
        
        LedgerLeg leg = new LedgerLeg();
        leg.setAmount(new BigDecimal("-10.00"));
        leg.setFromId(UUID.randomUUID());
        leg.setFromType(LedgerAccountType.CUSTOMER_CREDIT);
        leg.setToId(UUID.randomUUID());
        leg.setToType(LedgerAccountType.PLATFORM_CLEARING);
        cmd.setLegs(Collections.singletonList(leg));

        LedgerRejectedException ex = assertThrows(LedgerRejectedException.class, () -> service.record(cmd));
        assertTrue(ex.getMessage().contains("Amount must be positive"), ex.getMessage());
    }

    @Test
    void testSelfTransfer_ThrowsException() {
        LedgerTransactionCommand cmd = new LedgerTransactionCommand();
        cmd.setProducer("TEST");
        cmd.setReferenceId(UUID.randomUUID());
        cmd.setLeg("1");
        cmd.setTransactionId(DeterministicIdUtils.ledgerId("TEST", cmd.getReferenceId(), "1"));
        
        UUID commonId = UUID.randomUUID();
        LedgerLeg leg = new LedgerLeg();
        leg.setAmount(new BigDecimal("10.00"));
        leg.setFromId(commonId);
        leg.setFromType(LedgerAccountType.CUSTOMER_CREDIT);
        leg.setToId(commonId);
        leg.setToType(LedgerAccountType.CUSTOMER_CREDIT);
        cmd.setLegs(Collections.singletonList(leg));

        LedgerRejectedException ex = assertThrows(LedgerRejectedException.class, () -> service.record(cmd));
        assertTrue(ex.getMessage().contains("Self transfers not allowed"), ex.getMessage());
    }

    @Test
    void testInsufficientFunds_ThrowsException() {
        LedgerTransactionCommand cmd = new LedgerTransactionCommand();
        cmd.setProducer("TEST");
        cmd.setReferenceId(UUID.randomUUID());
        cmd.setLeg("1");
        cmd.setTransactionId(DeterministicIdUtils.ledgerId("TEST", cmd.getReferenceId(), "1"));
        
        LedgerLeg leg = new LedgerLeg();
        leg.setAmount(new BigDecimal("100.00"));
        leg.setFromId(UUID.randomUUID());
        leg.setFromType(LedgerAccountType.CUSTOMER_CREDIT);
        leg.setToId(UUID.randomUUID());
        leg.setToType(LedgerAccountType.PLATFORM_CLEARING);
        leg.setCategory(ChargeCategory.ORDER_TOTAL);
        cmd.setLegs(Collections.singletonList(leg));

        LedgerAccount sourceAcc = new LedgerAccount();
        sourceAcc.setId(UUID.randomUUID());
        sourceAcc.setKind(LedgerAccountType.Kind.PREPAID);
        sourceAcc.setBalance(new BigDecimal("50.00"));

        LedgerAccount destAcc = new LedgerAccount();
        destAcc.setId(UUID.randomUUID());
        destAcc.setKind(LedgerAccountType.Kind.INTERNAL);
        destAcc.setBalance(BigDecimal.ZERO);

        when(accountRepo.findByOwnerIdAndOwnerType(leg.getFromId(), leg.getFromType())).thenReturn(Optional.of(sourceAcc));
        when(accountRepo.findByOwnerIdAndOwnerType(leg.getToId(), leg.getToType())).thenReturn(Optional.of(destAcc));
        when(accountRepo.findByIdForUpdate(sourceAcc.getId())).thenReturn(Optional.of(sourceAcc));

        LedgerRejectedException ex = assertThrows(LedgerRejectedException.class, () -> service.record(cmd));
        assertTrue(ex.getMessage().contains("INSUFFICIENT_FUNDS"), ex.getMessage());
    }

    private LedgerTransactionCommand payableDebit(String amount, ChargeCategory category, String authorizedBy) {
        LedgerTransactionCommand cmd = new LedgerTransactionCommand();
        cmd.setProducer("TEST");
        cmd.setReferenceId(UUID.randomUUID());
        cmd.setLeg("1");
        cmd.setTransactionId(DeterministicIdUtils.ledgerId("TEST", cmd.getReferenceId(), "1"));

        LedgerLeg leg = new LedgerLeg();
        leg.setAmount(new BigDecimal(amount));
        leg.setFromId(UUID.randomUUID());
        leg.setFromType(LedgerAccountType.RESTAURANT_PAYABLE);
        leg.setToId(UUID.randomUUID());
        leg.setToType(LedgerAccountType.PLATFORM_CLEARING);
        leg.setCategory(category);
        leg.setAuthorizedBy(authorizedBy);
        cmd.setLegs(Collections.singletonList(leg));
        return cmd;
    }

    private void payableHolds(LedgerTransactionCommand cmd, String balance) {
        LedgerLeg leg = cmd.getLegs().get(0);
        LedgerAccount source = new LedgerAccount();
        source.setId(UUID.randomUUID());
        source.setKind(LedgerAccountType.Kind.PAYABLE);
        source.setBalance(new BigDecimal(balance));

        LedgerAccount dest = new LedgerAccount();
        dest.setId(UUID.randomUUID());
        dest.setKind(LedgerAccountType.Kind.INTERNAL);
        dest.setBalance(BigDecimal.ZERO);

        when(accountRepo.findByOwnerIdAndOwnerType(leg.getFromId(), leg.getFromType())).thenReturn(Optional.of(source));
        when(accountRepo.findByOwnerIdAndOwnerType(leg.getToId(), leg.getToType())).thenReturn(Optional.of(dest));
        when(accountRepo.findByIdForUpdate(source.getId())).thenReturn(Optional.of(source));
    }

    /**
     * A PAYABLE source must not go negative either.
     *
     * <p>The insufficient-funds case above uses a PREPAID source, so removing PAYABLE from the
     * balance rule left every test green — found on 2026-09-09 by performing the break-test Phase 2's
     * validation.md specified. A negative payable is the platform paying out money it does not owe.
     */
    @Test
    void testPayableCannotGoNegative() {
        LedgerTransactionCommand cmd = payableDebit("100.00", ChargeCategory.PAYOUT_TRANSFER, null);
        payableHolds(cmd, "50.00");

        LedgerRejectedException ex = assertThrows(LedgerRejectedException.class, () -> service.record(cmd));
        assertTrue(ex.getMessage().contains("INSUFFICIENT_FUNDS"), ex.getMessage());
    }

    /**
     * The one deliberate exception: an authorised clawback may take a payable negative, because the
     * payee owes the money back whether or not they still hold it.
     */
    @Test
    void testAuthorisedClawbackMayTakeAPayableNegative() {
        LedgerTransactionCommand cmd = payableDebit("100.00", ChargeCategory.CLAWBACK, "ADMIN");
        payableHolds(cmd, "50.00");

        service.record(cmd); // must not throw
    }

    /** An unauthorised clawback does not get the exception. */
    @Test
    void testUnauthorisedClawbackIsStillBounded() {
        LedgerTransactionCommand cmd = payableDebit("100.00", ChargeCategory.CLAWBACK, null);
        payableHolds(cmd, "50.00");

        LedgerRejectedException ex = assertThrows(LedgerRejectedException.class, () -> service.record(cmd));
        assertTrue(ex.getMessage().contains("INSUFFICIENT_FUNDS"), ex.getMessage());
    }
}
