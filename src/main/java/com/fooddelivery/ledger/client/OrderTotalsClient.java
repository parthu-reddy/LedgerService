package com.fooddelivery.ledger.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;

@FeignClient(name = "customer-application", fallback = OrderTotalsClientFallback.class)
public interface OrderTotalsClient {
    @GetMapping("/api/v1/internal/money/daily-totals")
    java.util.Map<String, BigDecimal> getDailyPaidOrderTotal(@RequestParam("date") LocalDate date);

    /** What the order book says was owed to restaurants and riders for that day's deliveries. */
    @GetMapping("/api/v1/internal/money/daily-payables")
    java.util.Map<String, BigDecimal> getDailyPayables(@RequestParam("date") LocalDate date);
}
