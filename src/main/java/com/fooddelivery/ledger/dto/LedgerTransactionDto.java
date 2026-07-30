package com.fooddelivery.ledger.dto;

import com.fooddelivery.common.enums.ChargeCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerTransactionDto {
    private UUID transactionId;
    private ChargeCategory category;
    private UUID fromAccountId;
    private UUID toAccountId;
    private BigDecimal amount;
    private LocalDateTime date;
}
