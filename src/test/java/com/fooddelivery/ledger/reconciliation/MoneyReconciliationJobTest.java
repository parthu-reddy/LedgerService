package com.fooddelivery.ledger.reconciliation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.transaction.annotation.Transactional;

@org.junit.jupiter.api.extension.ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class MoneyReconciliationJobTest {

    @org.mockito.InjectMocks
    private ReconciliationService reconciliationService;

    @org.mockito.Mock
    private ReconciliationBreakRepository breakRepository;

    @org.mockito.Mock
    private com.fooddelivery.ledger.client.PaymentTotalsClient paymentTotalsClient;

    @org.mockito.Mock
    private com.fooddelivery.ledger.client.OrderTotalsClient orderTotalsClient;

    @org.mockito.Mock
    private com.fooddelivery.ledger.client.WalletBalancesClient walletBalancesClient;

    @org.mockito.Mock
    private ReconciliationRunRepository runRepository;
    
    @org.mockito.Mock
    private com.fooddelivery.ledger.repository.ILedgerEntryRepository entryRepository;
    
    @org.mockito.Mock
    private com.fooddelivery.ledger.repository.ILedgerAccountRepository accountRepository;
    
    @org.mockito.Mock
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Test
    void executeRun_gatewayMismatch_createsBreak() {
        LocalDate date = LocalDate.now().minusDays(1);
        when(paymentTotalsClient.getDailyTotals(date)).thenReturn(Map.of(
            "capturedAmount", new BigDecimal("1000.00"),
            "refundedAmount", new BigDecimal("50.00")
        ));
        when(orderTotalsClient.getDailyPaidOrderTotal(date)).thenReturn(Map.of(
            "orderTotals", BigDecimal.ZERO
        ));
        when(walletBalancesClient.getBalances(anyInt(), anyInt())).thenReturn(org.springframework.data.domain.Page.empty());
        when(runRepository.save(any(ReconciliationRun.class))).thenReturn(new ReconciliationRun());
        when(entryRepository.sumByOwnerTypeAndDirectionAndDate(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(entryRepository.sumTotalDebitsByDate(any())).thenReturn(BigDecimal.ZERO);
        when(entryRepository.sumTotalCreditsByDate(any())).thenReturn(BigDecimal.ZERO);

        ReconciliationRun run = reconciliationService.executeRun(date);

        assertThat(run.getStatus()).isEqualTo("SUCCESS");
        
        org.mockito.ArgumentCaptor<ReconciliationBreak> breakCaptor = org.mockito.ArgumentCaptor.forClass(ReconciliationBreak.class);
        verify(breakRepository, times(1)).save(breakCaptor.capture());
        ReconciliationBreak capturedBreak = breakCaptor.getValue();
        assertThat(capturedBreak.getKind()).isEqualTo(BreakKind.GATEWAY_VS_LEDGER);
        assertThat(capturedBreak.getExpected()).isEqualByComparingTo("950.00");
    }

    @Test
    void idempotency_sameDayRerun_noDuplicates() {
        LocalDate date = LocalDate.now().minusDays(1);
        when(paymentTotalsClient.getDailyTotals(date)).thenReturn(Map.of(
            "capturedAmount", new BigDecimal("1000.00")
        ));
        when(orderTotalsClient.getDailyPaidOrderTotal(date)).thenReturn(Map.of());
        when(walletBalancesClient.getBalances(anyInt(), anyInt())).thenReturn(org.springframework.data.domain.Page.empty());
        when(runRepository.save(any(ReconciliationRun.class))).thenReturn(new ReconciliationRun());
        when(entryRepository.sumByOwnerTypeAndDirectionAndDate(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(entryRepository.sumTotalDebitsByDate(any())).thenReturn(BigDecimal.ZERO);
        when(entryRepository.sumTotalCreditsByDate(any())).thenReturn(BigDecimal.ZERO);

        when(breakRepository.existsByKindAndSubjectIdAndResolvedAtIsNull(any(), any()))
            .thenReturn(false)
            .thenReturn(true);

        reconciliationService.executeRun(date);
        reconciliationService.executeRun(date);

        verify(breakRepository, times(1)).save(any(ReconciliationBreak.class));
    }
}
