package com.fooddelivery.ledger.client;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Component
public class PaymentTotalsClientFallback implements PaymentTotalsClient {
    @Override
    public Map<String, BigDecimal> getDailyTotals(LocalDate date) {
        throw new ReconciliationClientException("PaymentService unavailable");
    }
}
