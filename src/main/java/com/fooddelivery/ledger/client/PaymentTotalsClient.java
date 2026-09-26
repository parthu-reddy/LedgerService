package com.fooddelivery.ledger.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;

@FeignClient(name = "payment-service", fallback = PaymentTotalsClientFallback.class)
public interface PaymentTotalsClient {
    @GetMapping("/api/v1/internal/payments/daily-totals")
    Map<String, BigDecimal> getDailyTotals(@RequestParam("from") java.time.Instant from, @RequestParam("to") java.time.Instant to, @RequestParam(value = "gatewayName", required = false) String gatewayName);
}
