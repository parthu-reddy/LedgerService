package com.fooddelivery.ledger.client;

import org.springframework.stereotype.Component;
import org.springframework.data.domain.Page;

@Component
public class WalletBalancesClientFallback implements WalletBalancesClient {
    @Override
    public Page<WalletBalanceDto> getBalances(int page, int size) {
        throw new ReconciliationClientException("WalletService unavailable");
    }
}
