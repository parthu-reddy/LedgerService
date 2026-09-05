package com.fooddelivery.ledger.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;

@FeignClient(name = "wallet-service", fallback = WalletBalancesClientFallback.class)
public interface WalletBalancesClient {
    // The Wallet object isn't strictly necessary to mirror completely, we just need a Map of UUID -> BigDecimal or a DTO.
    // Let's assume WalletService returns a custom wrapper or Page of a simplified DTO
    @GetMapping("/api/v1/internal/wallets/balances")
    Page<WalletBalanceDto> getBalances(@RequestParam("page") int page, @RequestParam("size") int size);
}
