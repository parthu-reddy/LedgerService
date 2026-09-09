package com.fooddelivery.ledger.client;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;

@Component
public class OrderTotalsClientFallback implements OrderTotalsClient {
    @Override
    public java.util.Map<String, BigDecimal> getDailyPaidOrderTotal(LocalDate date) {
        throw new ReconciliationClientException("CustomerApplication unavailable");
    }

    // Throws rather than returning zero: a zero would read as "the orders say nothing is owed" and
    // file a break for the whole day's payables against a service that was merely unreachable.
    // executeRun catches it and marks the run PARTIAL, which is the honest outcome.
    @Override
    public java.util.Map<String, BigDecimal> getDailyPayables(LocalDate date) {
        throw new ReconciliationClientException("CustomerApplication unavailable");
    }
}
