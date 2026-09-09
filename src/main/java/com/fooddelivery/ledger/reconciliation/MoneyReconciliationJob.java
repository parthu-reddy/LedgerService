package com.fooddelivery.ledger.reconciliation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

/**
 * <strong>@replication-safe: distributed-lock</strong> -- one replica runs the nightly
 * reconciliation; the others return immediately.
 *
 * <p>The marker said this already, and it was not true: the class took no lock at all. With more
 * than one LedgerService replica every one of them ran the full reconciliation at 02:00, each
 * writing its own {@code reconciliation_runs} row and racing the others on
 * {@code recordBreak}'s (kind, subjectId) dedup -- so the same condition could be recorded more than
 * once and the partial-run counter over-counted.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MoneyReconciliationJob {

    private final ReconciliationService reconciliationService;
    private final StringRedisTemplate redisTemplate;

    // GATEWAY_VS_LEDGER, ORDERS_VS_CLEARING, WALLET_VS_LEDGER, PAYABLE_VS_ORDERS, DOUBLE_ENTRY, STUCK
    @Scheduled(cron = "0 0 2 * * ?") // Nightly at 2 AM
    public void runNightlyReconciliation() {
        // Held for an hour: long enough that a slow run cannot be overtaken by a second replica,
        // short enough that a crashed replica does not block tomorrow night.
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                com.fooddelivery.common.constants.RedisKeyConstants.LOCK_MONEY_RECONCILIATION,
                "1", Duration.ofHours(1));
        if (!Boolean.TRUE.equals(acquired)) {
            log.info("Nightly money reconciliation is already running on another replica; skipping");
            return;
        }

        log.info("Starting nightly money reconciliation job");
        try {
            ReconciliationRun run = reconciliationService.executeRun(LocalDate.now().minusDays(1));
            log.info("Finished nightly money reconciliation job with status: {}", run.getStatus());
        } catch (Exception e) {
            log.error("Error executing nightly money reconciliation job", e);
        }
    }
}
