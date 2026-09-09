package com.fooddelivery.ledger.service;

import com.fooddelivery.common.constants.LedgerAccounts;
import com.fooddelivery.common.dto.ledger.CashSummaryDto;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.common.util.DeterministicIdUtils;
import com.fooddelivery.ledger.dto.CashRemittanceRequest;
import com.fooddelivery.ledger.entity.CashRemittance;
import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.repository.CashRemittanceRepository;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.repository.ILedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CashRemittanceTest {

    @Mock
    private CashRemittanceRepository cashRemittanceRepository;

    @Mock
    private DoubleEntryLedgerService doubleEntryLedgerService;

    @Mock
    private ILedgerAccountRepository accountRepository;

    @Mock
    private ILedgerEntryRepository entryRepository;

    @InjectMocks
    private CashService cashService;

    private final UUID driverId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    /** Cash the rider holds: CASH_RECEIVABLE carries a net-debit balance while cash is outstanding. */
    private void driverHolds(String amount) {
        when(accountRepository.findByOwnerIdAndOwnerType(driverId, LedgerAccountType.CASH_RECEIVABLE))
                .thenReturn(Optional.of(LedgerAccount.builder()
                        .id(accountId)
                        .ownerId(driverId)
                        .ownerType(LedgerAccountType.CASH_RECEIVABLE)
                        .balance(new BigDecimal(amount).negate())
                        .build()));
    }

    private CashRemittanceRequest request(String amount) {
        CashRemittanceRequest request = new CashRemittanceRequest();
        request.setDriverId(driverId);
        request.setAmount(new BigDecimal(amount));
        request.setReference("REF-1234");
        return request;
    }

    @Test
    void testRecordCashRemittance() {
        UUID adminId = UUID.randomUUID();
        driverHolds("500.00");
        when(cashRemittanceRepository.save(any(CashRemittance.class))).thenAnswer(i -> i.getArgument(0));

        CashRemittance remittance = cashService.recordCashRemittance(request("150.00"), adminId);

        assertNotNull(remittance);
        assertEquals(new BigDecimal("150.00"), remittance.getAmount());
        assertEquals("REF-1234", remittance.getReference());
        assertEquals(adminId, remittance.getRecordedBy());
        verify(cashRemittanceRepository).save(any(CashRemittance.class));
    }

    /**
     * A remittance must <em>discharge</em> the receivable, not add to it. Collecting cash debits
     * CASH_RECEIVABLE; the previous implementation debited it a second time on remittance, so the
     * same 500 rupees was booked as 1000 owed and cash in hand could never return to zero.
     */
    @Test
    void remittanceCreditsTheReceivableRatherThanDebitingItAgain() {
        driverHolds("500.00");
        when(cashRemittanceRepository.save(any(CashRemittance.class))).thenAnswer(i -> i.getArgument(0));

        cashService.recordCashRemittance(request("500.00"), UUID.randomUUID());

        ArgumentCaptor<LedgerTransactionCommand> captor = ArgumentCaptor.forClass(LedgerTransactionCommand.class);
        verify(doubleEntryLedgerService).record(captor.capture());
        List<LedgerLeg> legs = captor.getValue().getLegs();

        assertEquals(1, legs.size());
        LedgerLeg leg = legs.get(0);
        assertEquals(LedgerAccountType.BANK, leg.getFromType(), "money arrives in the bank, so BANK is the debit side");
        assertEquals(LedgerAccounts.BANK, leg.getFromId());
        assertEquals(LedgerAccountType.CASH_RECEIVABLE, leg.getToType(),
                "the rider's receivable is credited, clearing what they owe");
        assertEquals(driverId, leg.getToId());
        assertEquals(new BigDecimal("500.00"), leg.getAmount());
        assertEquals(ChargeCategory.CASH_REMITTED, leg.getCategory());
    }

    /** The ledger rejects any id it cannot re-derive from (producer, reference, leg). */
    @Test
    void ledgerTransactionIdIsDerivable() {
        driverHolds("500.00");
        when(cashRemittanceRepository.save(any(CashRemittance.class))).thenAnswer(i -> i.getArgument(0));

        CashRemittance remittance = cashService.recordCashRemittance(request("100.00"), UUID.randomUUID());

        ArgumentCaptor<LedgerTransactionCommand> captor = ArgumentCaptor.forClass(LedgerTransactionCommand.class);
        verify(doubleEntryLedgerService).record(captor.capture());
        LedgerTransactionCommand cmd = captor.getValue();

        assertEquals("REMIT", cmd.getLeg());
        assertEquals(remittance.getId(), cmd.getReferenceId());
        assertTrue(DeterministicIdUtils.isLedgerId(cmd.getTransactionId(), "ledger-service",
                remittance.getId().toString(), "REMIT"));
        assertEquals(cmd.getTransactionId(), remittance.getLedgerTransactionId());
    }

    /** Recording more cash than the rider is holding would credit the receivable into a surplus. */
    @Test
    void remittanceLargerThanCashInHandIsRefused() {
        driverHolds("100.00");

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> cashService.recordCashRemittance(request("250.00"), UUID.randomUUID()));

        assertTrue(e.getMessage().startsWith("REMITTANCE_EXCEEDS_CASH_IN_HAND"), e.getMessage());
        verify(doubleEntryLedgerService, never()).record(any());
        verify(cashRemittanceRepository, never()).save(any());
    }

    @Test
    void cashSummaryIsCollectedLessRemitted() {
        driverHolds("350.00");
        when(entryRepository.sumByAccountAndDirectionAndCategory(
                accountId, TransactionDirection.DEBIT, ChargeCategory.CASH_COLLECTED))
                .thenReturn(new BigDecimal("900.00"));
        when(entryRepository.sumByAccountAndDirectionAndCategory(
                accountId, TransactionDirection.CREDIT, ChargeCategory.CASH_REMITTED))
                .thenReturn(new BigDecimal("550.00"));

        CashSummaryDto summary = cashService.getCashSummary(driverId);

        assertEquals(new BigDecimal("900.00"), summary.getCashCollected());
        assertEquals(new BigDecimal("550.00"), summary.getCashRemitted());
        assertEquals(new BigDecimal("350.00"), summary.getCashInHand());
    }

    @Test
    void testGetCashRemittancesByDriver() {
        Pageable pageable = Pageable.unpaged();
        CashRemittance r = CashRemittance.builder().driverId(driverId).amount(BigDecimal.TEN).build();
        when(cashRemittanceRepository.findByDriverId(eq(driverId), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(r)));

        Page<CashRemittance> result = cashService.getCashRemittancesByDriver(driverId, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(BigDecimal.TEN, result.getContent().get(0).getAmount());
    }
}
