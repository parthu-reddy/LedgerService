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
}
