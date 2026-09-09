package com.fooddelivery.ledger.dto;

import com.fooddelivery.common.enums.ChargeCategory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@lombok.Data


public class LedgerTransactionDto {
    @jakarta.validation.constraints.NotNull
    private UUID transactionId;
    @jakarta.validation.constraints.NotNull
    private ChargeCategory category;
    @jakarta.validation.constraints.NotNull
    private UUID fromAccountId;
    @jakarta.validation.constraints.NotNull
    private UUID toAccountId;
    @jakarta.validation.constraints.NotNull
    private BigDecimal amount;
    @jakarta.validation.constraints.NotNull
    private java.time.OffsetDateTime date;


}
