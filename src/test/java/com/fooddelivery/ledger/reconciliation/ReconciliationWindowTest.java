package com.fooddelivery.ledger.reconciliation;

import com.fooddelivery.common.constants.RedisKeyConstants;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Defects D4 and D5 (RandomDocuments/TimezoneCorrectness_2026-09-25).
 *
 * <p>D4: each service used to decide "which day" itself with {@code CAST(created_at AS date)},
 * evaluated in its own database session's zone. D5: the nightly job fired at 02:00 in the JVM's zone
 * and reconciled the JVM's yesterday. Now the ledger decides both in the accounting zone and ships the
 * resulting instants. This build runs in Pacific/Chatham, so none of these can pass by the JVM zone
 * happening to agree.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReconciliationWindowTest {

    private static final Instant IST_MIDNIGHT_25TH = Instant.parse("2026-09-24T18:30:00Z");
    private static final Instant IST_MIDNIGHT_26TH = Instant.parse("2026-09-25T18:30:00Z");

    @Mock private ReconciliationBreakRepository breakRepository;
    @Mock private ReconciliationRunRepository runRepository;
    @Mock private com.fooddelivery.ledger.client.PaymentTotalsClient paymentTotalsClient;
    @Mock private com.fooddelivery.ledger.client.OrderTotalsClient orderTotalsClient;
    @Mock private com.fooddelivery.ledger.client.WalletBalancesClient walletBalancesClient;
    @Mock private com.fooddelivery.ledger.repository.ILedgerEntryRepository entryRepository;
    @Mock private com.fooddelivery.ledger.repository.ILedgerAccountRepository accountRepository;
    @Mock private com.fooddelivery.ledger.repository.ILedgerRejectionRepository rejectionRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private ReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(runRepository, breakRepository, accountRepository, entryRepository,
                paymentTotalsClient, orderTotalsClient, walletBalancesClient, new SimpleMeterRegistry(),
                rejectionRepository, AccountingCalendarFixture.KOLKATA);
        when(runRepository.save(any(ReconciliationRun.class))).thenAnswer(i -> i.getArgument(0));
        when(walletBalancesClient.getBalances(anyInt(), anyInt())).thenReturn(Page.empty());
        when(entryRepository.sumByOwnerAndDirectionInWindow(any(), any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(entryRepository.sumByOwnerTypeAndDirectionInWindow(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(entryRepository.sumTotalDebitsInWindow(any(), any())).thenReturn(BigDecimal.ZERO);
        when(entryRepository.sumTotalCreditsInWindow(any(), any())).thenReturn(BigDecimal.ZERO);
        when(entryRepository.findUnbalancedTransactionsInWindow(any(), any())).thenReturn(List.of());
        when(paymentTotalsClient.getDailyTotals(any(), any(), any())).thenReturn(Map.of());
        when(orderTotalsClient.getDailyPaidOrderTotal(any(), any())).thenReturn(Map.of());
        when(orderTotalsClient.getDailyPayables(any(), any())).thenReturn(Map.of());
    }

    @Test
    void oneAccountingDayIsTheSameWindowForEveryServiceAndEveryLedgerQuery() {
        service.executeRun(LocalDate.of(2026, 9, 25));

        verify(paymentTotalsClient).getDailyTotals(eq(IST_MIDNIGHT_25TH), eq(IST_MIDNIGHT_26TH), eq("CASHFREE"));
        verify(orderTotalsClient).getDailyPaidOrderTotal(IST_MIDNIGHT_25TH, IST_MIDNIGHT_26TH);
        verify(orderTotalsClient).getDailyPayables(IST_MIDNIGHT_25TH, IST_MIDNIGHT_26TH);
        verify(entryRepository).sumTotalDebitsInWindow(IST_MIDNIGHT_25TH, IST_MIDNIGHT_26TH);
        verify(entryRepository).sumTotalCreditsInWindow(IST_MIDNIGHT_25TH, IST_MIDNIGHT_26TH);
        verify(entryRepository).findUnbalancedTransactionsInWindow(IST_MIDNIGHT_25TH, IST_MIDNIGHT_26TH);
    }

    @Test
    void theNightlyRunReconcilesTheAccountingDayThatJustEnded() {
        // The fixture clock reads 2026-09-25T20:45Z: 02:15 on the 26th in Kolkata. "Yesterday" is the
        // 25th there, whatever the JVM zone says it is.
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(RedisKeyConstants.LOCK_MONEY_RECONCILIATION), any(), any(Duration.class))).thenReturn(true);

        new MoneyReconciliationJob(service, redisTemplate, AccountingCalendarFixture.KOLKATA).runNightlyReconciliation();

        verify(orderTotalsClient).getDailyPaidOrderTotal(IST_MIDNIGHT_25TH, IST_MIDNIGHT_26TH);
    }

    @Test
    void theNightlyScheduleFiresInTheAccountingZone() throws Exception {
        Scheduled scheduled = MoneyReconciliationJob.class.getMethod("runNightlyReconciliation").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 0 2 * * *");
        assertThat(scheduled.zone()).isEqualTo("${platform.accounting-zone}");
    }
}
