package com.fooddelivery.ledger.reconciliation;

import com.fooddelivery.common.constants.RedisKeyConstants;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The nightly job, and the run it drives.
 *
 * <p>Two gaps found 2026-09-09 performing Phase 5's break-test 4. Despite its name this class never
 * built a {@link MoneyReconciliationJob}: deleting the distributed lock entirely — the thing that
 * stops every replica reconciling the same night — left both tests green. And
 * {@code idempotency_sameDayRerun_noDuplicates} called {@code executeRun} twice and then asserted
 * nothing at all, so it could only ever fail by throwing.
 */
@ExtendWith(MockitoExtension.class)
class MoneyReconciliationJobTest {

    private ReconciliationService reconciliationService;

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

    @BeforeEach
    void setUp() {
        reconciliationService = new ReconciliationService(
                runRepository, breakRepository, accountRepository, entryRepository,
                paymentTotalsClient, orderTotalsClient, walletBalancesClient,
                new SimpleMeterRegistry(), rejectionRepository);
    }

    /** Every ledger read the run makes, answered with zero, so only the case under test moves. */
    private void allLedgerSumsAreZero() {
        // Lenient: this one is read only by checkGatewayVsLedger, which does not get that far in the
        // test where the payment client refuses. The other stubs here are reached by every run.
        org.mockito.Mockito.lenient()
                .when(entryRepository.sumByOwnerAndDirectionAndDate(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(entryRepository.sumByOwnerTypeAndDirectionAndDate(any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(entryRepository.sumTotalDebitsByDate(any())).thenReturn(BigDecimal.ZERO);
        when(entryRepository.sumTotalCreditsByDate(any())).thenReturn(BigDecimal.ZERO);
        when(entryRepository.findUnbalancedTransactionsByDate(any())).thenReturn(List.of());
        when(walletBalancesClient.getBalances(anyInt(), anyInt())).thenReturn(Page.empty());
        when(runRepository.save(any(ReconciliationRun.class))).thenAnswer(i -> i.getArgument(0));
        when(orderTotalsClient.getDailyPayables(any())).thenReturn(Map.of());
    }

    // ---------------------------------------------------------------- the job and its lock

    private MoneyReconciliationJob job() {
        return new MoneyReconciliationJob(reconciliationService, redisTemplate);
    }

    /** The replica that wins the lock does the work. */
    @Test
    void theReplicaThatTakesTheLockRunsTheReconciliation() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(RedisKeyConstants.LOCK_MONEY_RECONCILIATION), any(), any(Duration.class)))
                .thenReturn(true);
        allLedgerSumsAreZero();
        when(paymentTotalsClient.getDailyTotals(any(), any())).thenReturn(Map.of());
        when(orderTotalsClient.getDailyPaidOrderTotal(any())).thenReturn(Map.of());

        job().runNightlyReconciliation();

        verify(runRepository, atLeastOnce()).save(any(ReconciliationRun.class));
    }

    /**
     * The replicas that lose it do nothing. Without this, every replica writes its own run row and
     * they race each other on the (kind, subjectId) dedup, so one condition is recorded several times.
     */
    @Test
    void aReplicaThatLosesTheLockDoesNotReconcile() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(RedisKeyConstants.LOCK_MONEY_RECONCILIATION), any(), any(Duration.class)))
                .thenReturn(false);

        job().runNightlyReconciliation();

        verifyNoInteractions(runRepository, breakRepository, paymentTotalsClient, orderTotalsClient);
    }

    /** A Redis that answers null — the reply Spring gives inside an open transaction — is not a win. */
    @Test
    void anUndecidedLockIsTreatedAsLost() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(RedisKeyConstants.LOCK_MONEY_RECONCILIATION), any(), any(Duration.class)))
                .thenReturn(null);

        job().runNightlyReconciliation();

        verifyNoInteractions(runRepository, breakRepository);
    }

    /** The lock must expire, or one crashed replica blocks reconciliation for good. */
    @Test
    void theLockIsTakenWithAnExpiry() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(RedisKeyConstants.LOCK_MONEY_RECONCILIATION), any(), any(Duration.class)))
                .thenReturn(false);

        job().runNightlyReconciliation();

        org.mockito.ArgumentCaptor<Duration> ttl = org.mockito.ArgumentCaptor.forClass(Duration.class);
        verify(valueOperations).setIfAbsent(eq(RedisKeyConstants.LOCK_MONEY_RECONCILIATION), any(), ttl.capture());
        assertThat(ttl.getValue()).isNotNull();
        assertThat(ttl.getValue()).isBetween(Duration.ofMinutes(10), Duration.ofHours(6));
    }

    /** A failing run must still not leave the job throwing into the scheduler thread. */
    @Test
    void aFailingRunIsSwallowedSoTheScheduleSurvives() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq(RedisKeyConstants.LOCK_MONEY_RECONCILIATION), any(), any(Duration.class)))
                .thenReturn(true);
        when(runRepository.save(any(ReconciliationRun.class))).thenThrow(new RuntimeException("db down"));

        job().runNightlyReconciliation();
    }

    // ---------------------------------------------------------------- the run itself

    @Test
    void aGatewayMismatchIsRecordedAsABreak() {
        LocalDate date = LocalDate.now().minusDays(1);
        allLedgerSumsAreZero();
        when(paymentTotalsClient.getDailyTotals(eq(date), any())).thenReturn(Map.of(
                "capturedAmount", new BigDecimal("1000.00"),
                "refundedAmount", new BigDecimal("50.00")));
        when(orderTotalsClient.getDailyPaidOrderTotal(date)).thenReturn(Map.of("orderTotals", BigDecimal.ZERO));

        reconciliationService.executeRun(date);

        verify(breakRepository, atLeastOnce()).save(any(ReconciliationBreak.class));
    }

    /**
     * Re-running the same day must not file the condition again. The old version of this test ran
     * twice and asserted nothing; it is the save count that carries the claim.
     */
    @Test
    void reRunningTheSameDayDoesNotFileTheBreakTwice() {
        LocalDate date = LocalDate.now().minusDays(1);
        allLedgerSumsAreZero();
        when(paymentTotalsClient.getDailyTotals(eq(date), any()))
                .thenReturn(Map.of("capturedAmount", new BigDecimal("1000.00")));
        when(orderTotalsClient.getDailyPaidOrderTotal(date)).thenReturn(Map.of());

        // First run finds nothing on file; from then on the break is open.
        when(breakRepository.existsByKindAndSubjectIdAndResolvedAtIsNull(any(), any()))
                .thenReturn(false)
                .thenReturn(true);

        reconciliationService.executeRun(date);
        verify(breakRepository, times(1)).save(any(ReconciliationBreak.class));

        reconciliationService.executeRun(date);
        verify(breakRepository, times(1)).save(any(ReconciliationBreak.class));
    }

    /** A break already filed earlier today, even one since resolved, is not filed again. */
    @Test
    void aBreakAlreadyFiledTodayIsNotFiledAgain() {
        LocalDate date = LocalDate.now().minusDays(1);
        allLedgerSumsAreZero();
        when(paymentTotalsClient.getDailyTotals(eq(date), any()))
                .thenReturn(Map.of("capturedAmount", new BigDecimal("1000.00")));
        when(orderTotalsClient.getDailyPaidOrderTotal(date)).thenReturn(Map.of());
        when(breakRepository.existsByKindAndSubjectIdAndResolvedAtIsNull(any(), any())).thenReturn(false);
        when(breakRepository.existsByKindAndSubjectIdCreatedToday(any(), any())).thenReturn(true);

        reconciliationService.executeRun(date);

        verify(breakRepository, never()).save(any(ReconciliationBreak.class));
    }

    // ------------------------------------------------------- PAYABLE_VS_ORDERS

    /**
     * The check that used to be a stub.
     *
     * <p>{@code checkPayableVsOrders} logged "not fully implemented" and returned. One of the six
     * nightly checks had never looked at anything, so a restaurant or rider paid the wrong amount
     * would not have been reported by reconciliation at all.
     */
    @Test
    void aRestaurantPayableThatDisagreesWithTheOrdersIsABreak() {
        LocalDate date = LocalDate.now().minusDays(1);
        allLedgerSumsAreZero();
        when(paymentTotalsClient.getDailyTotals(any(), any())).thenReturn(Map.of());
        when(orderTotalsClient.getDailyPaidOrderTotal(any())).thenReturn(Map.of());
        // The orders say 900 was earned; the ledger booked nothing.
        when(orderTotalsClient.getDailyPayables(date))
                .thenReturn(Map.of("restaurantPayable", new BigDecimal("900.00")));

        reconciliationService.executeRun(date);

        org.mockito.ArgumentCaptor<ReconciliationBreak> filed =
                org.mockito.ArgumentCaptor.forClass(ReconciliationBreak.class);
        verify(breakRepository, atLeastOnce()).save(filed.capture());
        assertThat(filed.getAllValues())
                .anyMatch(b -> b.getKind() == BreakKind.PAYABLE_VS_ORDERS
                        && "RESTAURANT".equals(b.getSubjectType())
                        && b.getExpected().compareTo(new BigDecimal("900.00")) == 0);
    }

    /** Riders are the other half; a check that only looked at restaurants would still be half blind. */
    @Test
    void aDriverPayableThatDisagreesWithTheOrdersIsABreak() {
        LocalDate date = LocalDate.now().minusDays(1);
        allLedgerSumsAreZero();
        when(paymentTotalsClient.getDailyTotals(any(), any())).thenReturn(Map.of());
        when(orderTotalsClient.getDailyPaidOrderTotal(any())).thenReturn(Map.of());
        when(orderTotalsClient.getDailyPayables(date))
                .thenReturn(Map.of("driverPayable", new BigDecimal("150.50")));

        reconciliationService.executeRun(date);

        org.mockito.ArgumentCaptor<ReconciliationBreak> filed =
                org.mockito.ArgumentCaptor.forClass(ReconciliationBreak.class);
        verify(breakRepository, atLeastOnce()).save(filed.capture());
        assertThat(filed.getAllValues())
                .anyMatch(b -> b.getKind() == BreakKind.PAYABLE_VS_ORDERS
                        && "DRIVER".equals(b.getSubjectType()));
    }

    /** Agreement files nothing. A check that always fires is as useless as one that never does. */
    @Test
    void payablesThatMatchTheOrdersFileNoBreak() {
        LocalDate date = LocalDate.now().minusDays(1);
        allLedgerSumsAreZero();
        when(paymentTotalsClient.getDailyTotals(any(), any())).thenReturn(Map.of());
        when(orderTotalsClient.getDailyPaidOrderTotal(any())).thenReturn(Map.of());
        when(orderTotalsClient.getDailyPayables(date)).thenReturn(Map.of(
                "restaurantPayable", BigDecimal.ZERO, "driverPayable", BigDecimal.ZERO));

        reconciliationService.executeRun(date);

        verify(breakRepository, never()).save(org.mockito.ArgumentMatchers.argThat(
                b -> b != null && b.getKind() == BreakKind.PAYABLE_VS_ORDERS));
    }

    /**
     * An unreachable order service must mark the run PARTIAL, not file a break for the whole day.
     * A fallback returning zero would report every payable as unmatched.
     */
    @Test
    void anUnreachableOrderServiceMakesTheRunPartialRatherThanFilingABreak() {
        LocalDate date = LocalDate.now().minusDays(1);
        allLedgerSumsAreZero();
        when(paymentTotalsClient.getDailyTotals(any(), any())).thenReturn(Map.of());
        when(orderTotalsClient.getDailyPaidOrderTotal(any())).thenReturn(Map.of());
        when(orderTotalsClient.getDailyPayables(date))
                .thenThrow(new com.fooddelivery.ledger.client.ReconciliationClientException("down"));

        ReconciliationRun run = reconciliationService.executeRun(date);

        assertThat(run.getStatus()).isEqualTo("PARTIAL");
        verify(breakRepository, never()).save(org.mockito.ArgumentMatchers.argThat(
                b -> b != null && b.getKind() == BreakKind.PAYABLE_VS_ORDERS));
    }

    // ------------------------------------------- an unreachable dependency is not a zero

    /**
     * An unreachable payment service must make the run PARTIAL, not file a break per gateway.
     *
     * <p>{@code PaymentTotalsClientFallback} returned an empty map. {@code checkGatewayVsLedger}
     * reads {@code getOrDefault("capturedAmount", ZERO)} from it, so "the service is down" became
     * "the gateway captured nothing" — a GATEWAY_VS_LEDGER break for every gateway, on a run marked
     * SUCCESS because nothing threw.
     */
    @Test
    void anUnreachablePaymentServiceMakesTheRunPartialRatherThanFilingABreak() {
        LocalDate date = LocalDate.now().minusDays(1);
        allLedgerSumsAreZero();
        when(orderTotalsClient.getDailyPaidOrderTotal(any())).thenReturn(Map.of());
        when(orderTotalsClient.getDailyPayables(any())).thenReturn(Map.of());
        when(paymentTotalsClient.getDailyTotals(any(), any()))
                .thenThrow(new com.fooddelivery.ledger.client.ReconciliationClientException("down"));

        ReconciliationRun run = reconciliationService.executeRun(date);

        assertThat(run.getStatus()).isEqualTo("PARTIAL");
        verify(breakRepository, never()).save(org.mockito.ArgumentMatchers.argThat(
                b -> b != null && b.getKind() == BreakKind.GATEWAY_VS_LEDGER));
    }

    /**
     * The whole set, not the instance.
     *
     * <p>Three Feign clients feed the nightly run. Two threw on failure and one returned an empty
     * map, and that inconsistency is the entire defect above — so the rule is pinned for all of
     * them rather than for the one that happened to be wrong. A fallback that answers with an empty
     * collection or a zero cannot be told apart from a real reading of nothing.
     */
    @Test
    void everyReconciliationClientFallbackRefusesRatherThanAnsweringEmpty() {
        java.util.List<Object> fallbacks = java.util.List.of(
                new com.fooddelivery.ledger.client.OrderTotalsClientFallback(),
                new com.fooddelivery.ledger.client.PaymentTotalsClientFallback(),
                new com.fooddelivery.ledger.client.WalletBalancesClientFallback());

        for (Object fallback : fallbacks) {
            for (java.lang.reflect.Method m : fallback.getClass().getDeclaredMethods()) {
                if (m.isSynthetic() || m.getDeclaringClass() == Object.class) {
                    continue;
                }
                Object[] args = new Object[m.getParameterCount()];
                for (int i = 0; i < args.length; i++) {
                    Class<?> t = m.getParameterTypes()[i];
                    args[i] = t == int.class ? 0 : t == LocalDate.class ? LocalDate.now() : null;
                }
                try {
                    Object answer = m.invoke(fallback, args);
                    org.junit.jupiter.api.Assertions.fail(
                            fallback.getClass().getSimpleName() + "." + m.getName()
                            + " answered " + answer + " instead of refusing. The run cannot tell that"
                            + " apart from a real reading of nothing, so it files breaks for a"
                            + " dependency that was merely unreachable and still reports SUCCESS.");
                } catch (java.lang.reflect.InvocationTargetException e) {
                    assertThat(e.getCause())
                            .as(fallback.getClass().getSimpleName() + "." + m.getName())
                            .isInstanceOf(com.fooddelivery.ledger.client.ReconciliationClientException.class);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                }
            }
        }
    }
}
