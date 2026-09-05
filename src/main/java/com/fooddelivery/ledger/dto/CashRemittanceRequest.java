package com.fooddelivery.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CashRemittanceRequest {
    private UUID driverId;
    private BigDecimal amount;
    private String reference;
}
