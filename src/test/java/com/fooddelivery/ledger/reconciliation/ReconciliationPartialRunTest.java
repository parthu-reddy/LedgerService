package com.fooddelivery.ledger.reconciliation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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

public class ReconciliationPartialRunTest {

    @Test
    public void testPartialRun() {
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

        // Make checkGatewayVsLedger throw an exception
        when(pClient.getDailyTotals(any(), any())).thenThrow(new RuntimeException("API DOWN"));

        LocalDate date = LocalDate.of(2026, 9, 8);
        ReconciliationRun run = service.executeRun(date);

        assertEquals("PARTIAL", run.getStatus());
        assertEquals(1, registry.counter("money_reconciliation_partial_runs_total").count());
    }

    @Test
    public void testPartialRunWhenOrderApiFails() {
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

        when(oClient.getDailyPaidOrderTotal(any())).thenThrow(new RuntimeException("ORDER API DOWN"));

        LocalDate date = LocalDate.of(2026, 9, 8);
        ReconciliationRun run = service.executeRun(date);

        assertEquals("PARTIAL", run.getStatus());
    }
}
