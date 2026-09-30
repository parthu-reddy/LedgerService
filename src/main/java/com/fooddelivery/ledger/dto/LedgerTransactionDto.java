package com.fooddelivery.ledger.dto;

import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.TransactionDirection;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One immutable ledger movement shown by the administrator ledger explorer.
 *
 * <p>The explorer once collapsed the two sides of a double-entry transaction into a single row.
 * That erased the direction used by its filter: a CREDIT filter could visibly return the same
 * undirected row as a DEBIT filter. Each response row is now one authoritative entry, so the
 * {@link #direction} and {@link #accountId} always describe the amount on that row.</p>
 *
 * <p>{@code fromAccountId} and {@code toAccountId} remain only as compatibility projections for
 * callers that still render a transfer shape. New callers must use {@code accountId} and
 * {@code direction}; multi-leg transactions cannot be paired safely from the ledger-entry table.</p>
 */
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@lombok.Data
public class LedgerTransactionDto {
    @jakarta.validation.constraints.NotNull
    private UUID entryId;
    @jakarta.validation.constraints.NotNull
    private UUID transactionId;
    @jakarta.validation.constraints.NotNull
    private ChargeCategory category;
    @jakarta.validation.constraints.NotNull
    private UUID accountId;
    @jakarta.validation.constraints.NotNull
    private TransactionDirection direction;
    @Deprecated(forRemoval = true)
    private UUID fromAccountId;
    @Deprecated(forRemoval = true)
    private UUID toAccountId;
    @jakarta.validation.constraints.NotNull
    private BigDecimal amount;
    @jakarta.validation.constraints.NotNull
    private Instant date;
}
