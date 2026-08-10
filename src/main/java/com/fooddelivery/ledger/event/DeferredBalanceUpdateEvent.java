package com.fooddelivery.ledger.event;

import java.math.BigDecimal;
import java.util.UUID;

public class DeferredBalanceUpdateEvent {
    private final UUID accountId;
    private final BigDecimal amount;

    public DeferredBalanceUpdateEvent(UUID accountId, BigDecimal amount) {
        this.accountId = accountId;
        this.amount = amount;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
