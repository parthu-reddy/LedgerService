package com.fooddelivery.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", uniqueConstraints = {
    @jakarta.persistence.UniqueConstraint(columnNames = {"transaction_id", "direction"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerEntry {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "transaction_id")
    private UUID transactionId;
    @Column(name = "account_id")
    private UUID accountId;
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "direction")
    private com.fooddelivery.common.enums.TransactionDirection direction; // CREDIT, DEBIT
    
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "category", nullable = false)
    private com.fooddelivery.common.enums.ChargeCategory category;
    
    @Column(name = "amount")
    private BigDecimal amount;
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
