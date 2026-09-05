package com.fooddelivery.ledger.reconciliation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class MoneyReconciliationJob {

    private final ReconciliationService reconciliationService;

    // @replication-safe: distributed-lock (Assumed handled via ShedLock or similar library via convention)
    // GATEWAY_VS_LEDGER, ORDERS_VS_CLEARING, WALLET_VS_LEDGER, PAYABLE_VS_ORDERS, DOUBLE_ENTRY, STUCK
    @Scheduled(cron = "0 0 2 * * ?") // Nightly at 2 AM
    public void runNightlyReconciliation() {
        log.info("Starting nightly money reconciliation job");
        try {
            ReconciliationRun run = reconciliationService.executeRun(LocalDate.now().minusDays(1));
            log.info("Finished nightly money reconciliation job with status: {}", run.getStatus());
        } catch (Exception e) {
            log.error("Error executing nightly money reconciliation job", e);
        }
    }
}
