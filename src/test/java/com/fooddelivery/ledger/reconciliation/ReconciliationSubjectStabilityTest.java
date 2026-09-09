package com.fooddelivery.ledger.reconciliation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;
import com.fooddelivery.ledger.client.OrderTotalsClient;
import com.fooddelivery.ledger.client.PaymentTotalsClient;
import com.fooddelivery.ledger.client.WalletBalancesClient;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.repository.ILedgerEntryRepository;
import com.fooddelivery.ledger.repository.ILedgerRejectionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.math.BigDecimal;

public class ReconciliationSubjectStabilityTest {
    
    @Test
    public void testSubjectStability() {
        ReconciliationRunRepository runRepo = mock(ReconciliationRunRepository.class);
        ReconciliationBreakRepository breakRepo = mock(ReconciliationBreakRepository.class);
        ILedgerAccountRepository accRepo = mock(ILedgerAccountRepository.class);
        ILedgerEntryRepository entryRepo = mock(ILedgerEntryRepository.class);
        PaymentTotalsClient pClient = mock(PaymentTotalsClient.class);
        OrderTotalsClient oClient = mock(OrderTotalsClient.class);
        WalletBalancesClient wClient = mock(WalletBalancesClient.class);
        ILedgerRejectionRepository rejRepo = mock(ILedgerRejectionRepository.class);
        MeterRegistry registry = new SimpleMeterRegistry();

        ReconciliationService service = new ReconciliationService(
            runRepo, breakRepo, accRepo, entryRepo, pClient, oClient, wClient, registry, rejRepo
        );

        when(runRepo.save(any(ReconciliationRun.class))).thenAnswer(i -> i.getArgument(0));

        // Setup a discrepancy for GATEWAY_VS_LEDGER
        java.util.Map<String, BigDecimal> pt = new java.util.HashMap<>();
        pt.put("capturedAmount", new BigDecimal("100.00"));
        when(pClient.getDailyTotals(any(), any())).thenReturn(pt);
        when(entryRepo.sumByOwnerAndDirectionAndDate(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(entryRepo.sumByOwnerTypeAndDirectionAndDate(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        
        // Setup a discrepancy for ORDERS_VS_CLEARING
        java.util.Map<String, BigDecimal> ot = new java.util.HashMap<>();
        ot.put("orderTotals", new BigDecimal("50.00"));
        when(oClient.getDailyPaidOrderTotal(any())).thenReturn(ot);

        LocalDate date = LocalDate.of(2026, 9, 8);
        service.executeRun(date);

        ArgumentCaptor<ReconciliationBreak> breakCaptor = ArgumentCaptor.forClass(ReconciliationBreak.class);
        verify(breakRepo, atLeast(2)).save(breakCaptor.capture());
        
        java.util.List<ReconciliationBreak> breaks = breakCaptor.getAllValues();
        java.util.UUID gatewayBreakId1 = breaks.stream().filter(b -> b.getKind() == BreakKind.GATEWAY_VS_LEDGER && b.getSubjectType().equals("GATEWAY")).findFirst().get().getSubjectId();
        java.util.UUID ordersBreakId1 = breaks.stream().filter(b -> b.getKind() == BreakKind.ORDERS_VS_CLEARING).findFirst().get().getSubjectId();

        clearInvocations(breakRepo);

        // Run again
        service.executeRun(date);

        ArgumentCaptor<ReconciliationBreak> breakCaptor2 = ArgumentCaptor.forClass(ReconciliationBreak.class);
        verify(breakRepo, atLeast(2)).save(breakCaptor2.capture()); 

        java.util.List<ReconciliationBreak> breaks2 = breakCaptor2.getAllValues();
        java.util.UUID gatewayBreakId2 = breaks2.stream().filter(b -> b.getKind() == BreakKind.GATEWAY_VS_LEDGER && b.getSubjectType().equals("GATEWAY")).findFirst().get().getSubjectId();
        java.util.UUID ordersBreakId2 = breaks2.stream().filter(b -> b.getKind() == BreakKind.ORDERS_VS_CLEARING).findFirst().get().getSubjectId();

        assertEquals(gatewayBreakId1, gatewayBreakId2, "Gateway subject ID should be stable for the same date");
        assertEquals(ordersBreakId1, ordersBreakId2, "Orders subject ID should be stable for the same date");
    }

    @Test
    public void testServiceInstantiates() {
        MeterRegistry registry = new SimpleMeterRegistry();
        ReconciliationService service = new ReconciliationService(
            null, null, null, null, null, null, null, registry, null
        );
        assertNotNull(service);
    }
}
