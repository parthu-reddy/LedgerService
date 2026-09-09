package com.fooddelivery.ledger.reconciliation;

import com.fooddelivery.ledger.client.OrderTotalsClient;
import com.fooddelivery.ledger.client.PaymentTotalsClient;
import com.fooddelivery.ledger.client.WalletBalancesClient;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.repository.ILedgerEntryRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Counter;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.charset.StandardCharsets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import java.util.Map;

@Service

@Slf4j
public class ReconciliationService {

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationBreakRepository breakRepository;
    private final ILedgerAccountRepository accountRepository;
    private final ILedgerEntryRepository entryRepository;
    private final PaymentTotalsClient paymentTotalsClient;
    private final OrderTotalsClient orderTotalsClient;
    private final WalletBalancesClient walletBalancesClient;
    private final MeterRegistry meterRegistry;
    private final com.fooddelivery.ledger.repository.ILedgerRejectionRepository rejectionRepository;
    private final java.util.concurrent.ConcurrentHashMap<BreakKind, AtomicLong> breakCounters = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicLong lastSuccessEpoch;
    private final Counter partialRunsCounter;

    public ReconciliationService(ReconciliationRunRepository runRepository,
                                 ReconciliationBreakRepository breakRepository,
                                 ILedgerAccountRepository accountRepository,
                                 ILedgerEntryRepository entryRepository,
                                 PaymentTotalsClient paymentTotalsClient,
                                 OrderTotalsClient orderTotalsClient,
                                 WalletBalancesClient walletBalancesClient,
                                 MeterRegistry meterRegistry,
                                 com.fooddelivery.ledger.repository.ILedgerRejectionRepository rejectionRepository) {
        this.runRepository = runRepository;
        this.breakRepository = breakRepository;
        this.accountRepository = accountRepository;
        this.entryRepository = entryRepository;
        this.paymentTotalsClient = paymentTotalsClient;
        this.orderTotalsClient = orderTotalsClient;
        this.walletBalancesClient = walletBalancesClient;
        this.meterRegistry = meterRegistry;
        this.rejectionRepository = rejectionRepository;
        this.lastSuccessEpoch = meterRegistry.gauge("money_reconciliation_last_success_epoch", new AtomicLong(0));
        this.partialRunsCounter = meterRegistry.counter("money_reconciliation_partial_runs_total");
        // Unresolved breaks is a gauge read from the table, not a counter. A counter only ever goes
        // up, so `> 0` fired from the first break the platform ever had and could not be cleared by
        // resolving anything -- the alert became permanent noise, which is what Phase 6 set out to
        // fix. Registering metric names that nothing feeds is worse than leaving them absent: the
        // series exists, reads zero forever, and looks healthy.
        Gauge.builder("money_reconciliation_unresolved_breaks", this,
                        s -> s.breakRepository.countByResolvedAtIsNull())
             .description("Reconciliation breaks that nobody has resolved yet")
             .register(meterRegistry);
        this.stuckRejections = meterRegistry.gauge("money_ledger_unresolved_rejections", new AtomicLong(0));
    }

    private final AtomicLong stuckRejections;

    private void recordBreak(ReconciliationBreak rBreak) {
        if (breakRepository.existsByKindAndSubjectIdAndResolvedAtIsNull(rBreak.getKind(), rBreak.getSubjectId()) || 
            breakRepository.existsByKindAndSubjectIdCreatedToday(rBreak.getKind(), rBreak.getSubjectId())) {
            return;
        }
        breakRepository.save(rBreak);
        // Per-kind gauge, read from the table so resolving a break lowers it again.
        breakCounters.computeIfAbsent(rBreak.getKind(), k -> {
            AtomicLong counter = new AtomicLong(0);
            Gauge.builder("money_reconciliation_breaks", counter, AtomicLong::get)
                 .tag("kind", k.name())
                 .register(meterRegistry);
            return counter;
        });
        for (Map.Entry<BreakKind, AtomicLong> e : breakCounters.entrySet()) {
            e.getValue().set(breakRepository.countByKindAndResolvedAtIsNull(e.getKey()));
        }
    }

    public ReconciliationRun executeRun(LocalDate targetDate) {
        ReconciliationRun run = ReconciliationRun.builder()
                .id(UUID.randomUUID())
                .startedAt(OffsetDateTime.now())
                .status("STARTED")
                .build();
        run = runRepository.save(run);

        boolean partial = false;

        try {
            checkGatewayVsLedger(run, targetDate);
        } catch (Exception e) {
            log.error("GATEWAY_VS_LEDGER failed", e);
            partial = true;
        }

        try {
            checkOrdersVsClearing(run, targetDate);
        } catch (Exception e) {
            log.error("ORDERS_VS_CLEARING failed", e);
            partial = true;
        }

        try {
            checkWalletVsLedger(run, targetDate);
        } catch (Exception e) {
            log.error("WALLET_VS_LEDGER failed", e);
            partial = true;
        }

        try {
            checkPayableVsOrders(run, targetDate);
        } catch (Exception e) {
            log.error("PAYABLE_VS_ORDERS failed", e);
            partial = true;
        }

        try {
            checkDoubleEntry(run, targetDate);
        } catch (Exception e) {
            log.error("DOUBLE_ENTRY failed", e);
            partial = true;
        }

        try {
            checkStuck(run, targetDate);
        } catch (Exception e) {
            log.error("STUCK failed", e);
            partial = true;
        }

        run.setFinishedAt(OffsetDateTime.now());
        run.setStatus(partial ? "PARTIAL" : "SUCCESS");
        run = runRepository.save(run);
        if (partial) {
            partialRunsCounter.increment();
        } else {
            lastSuccessEpoch.set(System.currentTimeMillis() / 1000);
        }
        return run;
    }

    private void checkGatewayVsLedger(ReconciliationRun run, LocalDate date) {
        for (com.fooddelivery.common.enums.PaymentGateway gateway : com.fooddelivery.common.enums.PaymentGateway.values()) {
            java.util.Map<String, BigDecimal> totals = paymentTotalsClient.getDailyTotals(date, gateway.name());
            BigDecimal expectedCaptured = totals.getOrDefault("capturedAmount", BigDecimal.ZERO);
            BigDecimal expectedRefunded = totals.getOrDefault("refundedAmount", BigDecimal.ZERO);

            // Against this gateway's own receivable account. Summing every gateway's movement and
            // comparing it to one gateway's expected total made every gateway break as soon as a
            // second one carried any traffic.
            UUID gatewayAccountOwner = com.fooddelivery.common.constants.LedgerAccounts.gatewayOwnerId(gateway);
            // A capture debits GATEWAY_RECEIVABLE and a refund credits it back, mirroring
            // LedgerBookkeeper.bookPaymentCaptured / bookRefund.
            BigDecimal actualCaptured = entryRepository.sumByOwnerAndDirectionAndDate(
                    gatewayAccountOwner,
                    com.fooddelivery.common.enums.LedgerAccountType.GATEWAY_RECEIVABLE,
                    com.fooddelivery.common.enums.TransactionDirection.DEBIT, date);
            BigDecimal actualRefunded = entryRepository.sumByOwnerAndDirectionAndDate(
                    gatewayAccountOwner,
                    com.fooddelivery.common.enums.LedgerAccountType.GATEWAY_RECEIVABLE,
                    com.fooddelivery.common.enums.TransactionDirection.CREDIT, date);

            if (expectedCaptured.compareTo(actualCaptured) != 0 || expectedRefunded.compareTo(actualRefunded) != 0) {
                UUID deterministicId = gatewayAccountOwner;
                ReconciliationBreak rBreak = ReconciliationBreak.builder()
                        .id(UUID.randomUUID())
                        .runId(run.getId())
                        .kind(BreakKind.GATEWAY_VS_LEDGER)
                        .subjectType("GATEWAY")
                        .subjectId(deterministicId)
                        .expected(expectedCaptured.subtract(expectedRefunded))
                        .actual(actualCaptured.subtract(actualRefunded))
                        .detail("{\"gateway\": \"" + gateway.name() + "\", \"expectedCaptured\": " + expectedCaptured + ", \"actualCaptured\": " + actualCaptured + "}")
                        .build();
                recordBreak(rBreak);
            }
        }
    }

    private void checkOrdersVsClearing(ReconciliationRun run, LocalDate date) {
        java.util.Map<String, BigDecimal> orderTotalsMap = orderTotalsClient.getDailyPaidOrderTotal(date);
        BigDecimal expectedOrders = orderTotalsMap.getOrDefault("orderTotals", BigDecimal.ZERO);

        BigDecimal actualClearing = entryRepository.sumByOwnerTypeAndDirectionAndDate(
                com.fooddelivery.common.enums.LedgerAccountType.PLATFORM_CLEARING,
                com.fooddelivery.common.enums.TransactionDirection.CREDIT, date);

        if (expectedOrders.compareTo(actualClearing) != 0) {
            UUID deterministicId = UUID.nameUUIDFromBytes(("ORDERS_VS_CLEARING_" + date).getBytes(StandardCharsets.UTF_8));
            ReconciliationBreak rBreak = ReconciliationBreak.builder()
                    .id(UUID.randomUUID())
                    .runId(run.getId())
                    .kind(BreakKind.ORDERS_VS_CLEARING)
                    .subjectType("SYSTEM")
                    .subjectId(deterministicId)
                    .expected(expectedOrders)
                    .actual(actualClearing)
                    .detail("{}")
                    .build();
            recordBreak(rBreak);
        }
    }

    private void checkWalletVsLedger(ReconciliationRun run, LocalDate date) {
        int page = 0;
        int size = 100;
        org.springframework.data.domain.Page<com.fooddelivery.ledger.client.WalletBalanceDto> walletPage;

        do {
            walletPage = walletBalancesClient.getBalances(page, size);
            for (com.fooddelivery.ledger.client.WalletBalanceDto wallet : walletPage.getContent()) {
                com.fooddelivery.common.enums.LedgerAccountType expectedType =
                        "CUSTOMER".equals(wallet.getEntityType()) ?
                                com.fooddelivery.common.enums.LedgerAccountType.CUSTOMER_CREDIT :
                                com.fooddelivery.common.enums.LedgerAccountType.ADVERTISER_PREPAID;

                accountRepository.findByOwnerIdAndOwnerType(wallet.getEntityId(), expectedType)
                        .ifPresent(account -> {
                            if (account.getBalance().compareTo(wallet.getBalance()) != 0) {
                                ReconciliationBreak rBreak = ReconciliationBreak.builder()
                                        .id(UUID.randomUUID())
                                        .runId(run.getId())
                                        .kind(BreakKind.WALLET_VS_LEDGER)
                                        .subjectType("WALLET")
                                        .subjectId(wallet.getId())
                                        .expected(wallet.getBalance())
                                        .actual(account.getBalance())
                                        .detail("{\"entityId\": \"" + wallet.getEntityId() + "\"}")
                                        .build();
                                recordBreak(rBreak);
                            }
                        });
            }
            page++;
        } while (walletPage.hasNext());
    }

    /**
     * What the orders say is owed, against what the ledger actually booked as payable.
     *
     * <p>This was a stub that logged a warning: one of the six nightly checks had never run. It
     * went unnoticed because the gate grepped {@code MoneyReconciliationJob.java} -- a class whose
     * only content is the lock and one call -- for the kind names, and found them in a comment.
     * Found 2026-09-09 performing Phase 7's break-test 1.
     *
     * <p>Both sides are dated on the delivery, because {@code bookDelivered} raises the payable
     * when the order is delivered. A divergence means the pricing matrix and the ledger disagree
     * about what a delivery earned -- the case where a restaurant or rider is paid the wrong amount.
     */
    private void checkPayableVsOrders(ReconciliationRun run, LocalDate date) {
        java.util.Map<String, BigDecimal> owed = orderTotalsClient.getDailyPayables(date);

        record Side(String name, String key, com.fooddelivery.common.enums.LedgerAccountType account) {
        }

        for (Side side : new Side[]{
                new Side("RESTAURANT", "restaurantPayable", com.fooddelivery.common.enums.LedgerAccountType.RESTAURANT_PAYABLE),
                new Side("DRIVER", "driverPayable", com.fooddelivery.common.enums.LedgerAccountType.DRIVER_PAYABLE)}) {

            BigDecimal expected = owed.getOrDefault(side.key(), BigDecimal.ZERO);
            BigDecimal actual = entryRepository.sumByOwnerTypeAndDirectionAndDate(
                    side.account(), com.fooddelivery.common.enums.TransactionDirection.CREDIT, date);

            if (expected.compareTo(actual) != 0) {
                UUID deterministicId = UUID.nameUUIDFromBytes(
                        ("PAYABLE_VS_ORDERS_" + side.name() + "_" + date).getBytes(StandardCharsets.UTF_8));
                recordBreak(ReconciliationBreak.builder()
                        .id(UUID.randomUUID())
                        .runId(run.getId())
                        .kind(BreakKind.PAYABLE_VS_ORDERS)
                        .subjectType(side.name())
                        .subjectId(deterministicId)
                        .expected(expected)
                        .actual(actual)
                        .detail("{\"side\":\"" + side.name() + "\"}")
                        .build());
            }
        }
    }

    private void checkDoubleEntry(ReconciliationRun run, LocalDate date) {
        BigDecimal totalDebits = entryRepository.sumTotalDebitsByDate(date);
        BigDecimal totalCredits = entryRepository.sumTotalCreditsByDate(date);

        if (totalDebits.compareTo(totalCredits) != 0) {
            UUID deterministicId = UUID.nameUUIDFromBytes(("DOUBLE_ENTRY_" + date).getBytes(StandardCharsets.UTF_8));
            ReconciliationBreak rBreak = ReconciliationBreak.builder()
                    .id(UUID.randomUUID())
                    .runId(run.getId())
                    .kind(BreakKind.DOUBLE_ENTRY)
                    .subjectType("LEDGER")
                    .subjectId(deterministicId)
                    .expected(totalDebits)
                    .actual(totalCredits)
                    .detail("{\"error\": \"Overall totals mismatch\"}")
                    .build();
            recordBreak(rBreak);
        }

        // Per-transaction check
        List<UUID> unbalancedTransactions = entryRepository.findUnbalancedTransactionsByDate(date);
        for (UUID txnId : unbalancedTransactions) {
            ReconciliationBreak txnBreak = ReconciliationBreak.builder()
                    .id(UUID.randomUUID())
                    .runId(run.getId())
                    .kind(BreakKind.DOUBLE_ENTRY)
                    .subjectType("TRANSACTION")
                    .subjectId(txnId)
                    .expected(BigDecimal.ZERO)
                    .actual(BigDecimal.ONE)
                    .detail("{\"error\": \"Transaction is unbalanced\"}")
                    .build();
            recordBreak(txnBreak);
        }
    }

    private void checkStuck(ReconciliationRun run, LocalDate date) {
        // Find unresolved rejections older than 1 hour
        long oldRejections = rejectionRepository.countByResolvedAtIsNullAndCreatedAtBefore(
                java.time.OffsetDateTime.now().minusHours(1));
        stuckRejections.set(oldRejections);

        if (oldRejections > 0) {
            UUID deterministicId = UUID.nameUUIDFromBytes(("STUCK_REJECTIONS_" + date).getBytes(StandardCharsets.UTF_8));
            ReconciliationBreak rBreak = ReconciliationBreak.builder()
                    .id(UUID.randomUUID())
                    .runId(run.getId())
                    .kind(BreakKind.STUCK)
                    .subjectType("SYSTEM")
                    .subjectId(deterministicId)
                    .expected(BigDecimal.ZERO)
                    .actual(new BigDecimal(oldRejections))
                    .detail("{\"error\": \"Unresolved rejections older than 1 hour\"}")
                    .build();
            recordBreak(rBreak);
        }
    }
}
