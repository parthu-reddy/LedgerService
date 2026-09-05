package com.fooddelivery.ledger.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalanceDto {
    private UUID id;
    private UUID entityId;
    private String entityType;
    private BigDecimal balance;
}
