package com.fooddelivery.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.OffsetDateTime;

import jakarta.persistence.Index;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.common.enums.ChargeCategory;

@Entity
@Table(name = "ledger_entries", 
       uniqueConstraints = {@jakarta.persistence.UniqueConstraint(columnNames = {"transaction_id", "account_id", "direction"})},
       indexes = {
           @Index(name = "idx_entries_reference_id", columnList = "reference_id"),
           @Index(name = "idx_entries_account_created", columnList = "account_id, created_at"),
           @Index(name = "idx_entries_transaction_id", columnList = "transaction_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {
    @Id
    @Column(name = "id")
    @NotNull
    private UUID id;

    @Column(name = "transaction_id", nullable = false)
    @NotNull
    private UUID transactionId;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Column(name = "account_id", nullable = false)
    @NotNull
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 6)
    @NotNull
    private TransactionDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    @NotNull
    private ChargeCategory category;

    @Column(name = "amount", nullable = false)
    @NotNull
    private BigDecimal amount;

    @Column(name = "producer", nullable = false, length = 64)
    private String producer;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "authorized_by", length = 64)
    private String authorizedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @NotNull
    private OffsetDateTime createdAt;
}
