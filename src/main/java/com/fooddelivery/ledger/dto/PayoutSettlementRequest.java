package com.fooddelivery.ledger.dto;

import com.fooddelivery.common.enums.AccountType;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PayoutSettlementRequest {
    private UUID ownerId;
    private AccountType ownerType;
    private BigDecimal amount;
}
