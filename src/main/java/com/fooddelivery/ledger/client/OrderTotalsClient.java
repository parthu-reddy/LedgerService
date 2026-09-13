package com.fooddelivery.ledger.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;

// "customer-service", not "customer-application": that is the name CustomerApplication registers
// (spring.application.name), and the name every other client uses -- DeliveryExecutiveApplication,
// ONDCIntegrationService, RestaurantApplication, common-web, and the two sibling clients in this
// package. Targeting an unregistered name meant this client never resolved, the fallback threw on
// every call, and executeRun marked every reconciliation run PARTIAL. Found 2026-09-12 by
// validate_phase2_paths.py; unrelated to the CommonLibrary split, last touched 2026-09-09.
@FeignClient(name = "customer-service", contextId = "ledgerOrderTotalsClient",
        fallback = OrderTotalsClientFallback.class)
public interface OrderTotalsClient {
    @GetMapping("/api/v1/internal/money/daily-totals")
    java.util.Map<String, BigDecimal> getDailyPaidOrderTotal(@RequestParam("date") LocalDate date);

    /** What the order book says was owed to restaurants and riders for that day's deliveries. */
    @GetMapping("/api/v1/internal/money/daily-payables")
    java.util.Map<String, BigDecimal> getDailyPayables(@RequestParam("date") LocalDate date);
}
