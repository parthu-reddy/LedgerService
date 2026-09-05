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
        
        LedgerRejectedException ex = assertThrows(LedgerRejectedException.class, () -> service.record(cmd));
        assertTrue(ex.getMessage().contains("must be a UUID v5"));
    }

    @Test
    void testNegativeAmount_ThrowsException() {
        LedgerTransactionCommand cmd = new LedgerTransactionCommand();
        // create a UUID v5
        cmd.setProducer("TEST");
        cmd.setReferenceId(UUID.randomUUID());
        cmd.setTransactionId(DeterministicIdUtils.ledgerId("TEST", cmd.getReferenceId(), "1"));
        
        LedgerLeg leg = new LedgerLeg();
        leg.setAmount(new BigDecimal("-10.00"));
        leg.setFromId(UUID.randomUUID());
        leg.setFromType(LedgerAccountType.CUSTOMER_CREDIT);
        leg.setToId(UUID.randomUUID());
        leg.setToType(LedgerAccountType.PLATFORM_CLEARING);
        cmd.setLegs(Collections.singletonList(leg));

        LedgerRejectedException ex = assertThrows(LedgerRejectedException.class, () -> service.record(cmd));
        assertTrue(ex.getMessage().contains("Amount must be positive"));
    }

    @Test
    void testSelfTransfer_ThrowsException() {
        LedgerTransactionCommand cmd = new LedgerTransactionCommand();
        cmd.setProducer("TEST");
        cmd.setReferenceId(UUID.randomUUID());
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
        assertTrue(ex.getMessage().contains("Self transfers not allowed"));
    }

    @Test
    void testInsufficientFunds_ThrowsException() {
        LedgerTransactionCommand cmd = new LedgerTransactionCommand();
        cmd.setProducer("TEST");
        cmd.setReferenceId(UUID.randomUUID());
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

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.record(cmd));
        assertTrue(ex.getMessage().contains("INSUFFICIENT_FUNDS"));
    }
}
