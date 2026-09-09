package com.fooddelivery.ledger.service;

import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.entity.PayoutStatus;
import com.fooddelivery.ledger.repository.PayoutRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Feeds the money alerts that had no producer.
 *
 * <p>{@code payout_approved_time_days} was registered as a constant zero purely so a static check
 * that greps alert expressions against the source would pass. A series that exists and never moves
 * is worse than a missing one: the alert looks wired and can never fire. This reads the real
 * condition.
 *
 * <p><strong>@replication-safe: idempotent</strong> -- reads one aggregate and publishes it as a
 * gauge. Every replica exporting its own view of the same query is correct, and Prometheus
 * scrapes them all; a lock would leave every replica but one reporting a stale zero.
 */
@Component
@Slf4j
public class MoneyAlertMetrics {

    private final PayoutRepository payoutRepository;
    private final AtomicLong approvedNotPaidDays = new AtomicLong(0);

    public MoneyAlertMetrics(PayoutRepository payoutRepository, MeterRegistry meterRegistry) {
        this.payoutRepository = payoutRepository;
        Gauge.builder("payout_approved_time_days", approvedNotPaidDays, AtomicLong::doubleValue)
             .description("Age in days of the oldest payout approved but not yet paid")
             .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${money.alert-metrics.refresh-ms:60000}")
    public void refresh() {
        try {
            approvedNotPaidDays.set(oldestApprovedAgeInDays());
        } catch (Exception e) {
            log.warn("Could not refresh money alert metrics: {}", e.getMessage());
        }
    }

    long oldestApprovedAgeInDays() {
        Optional<Payout> oldest = payoutRepository.findFirstByStatusOrderByApprovedAtAsc(PayoutStatus.APPROVED);
        if (oldest.isEmpty() || oldest.get().getApprovedAt() == null) {
            return 0L;
        }
        return Math.max(0L, Duration.between(oldest.get().getApprovedAt(), OffsetDateTime.now()).toDays());
    }
}
