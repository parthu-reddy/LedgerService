package com.fooddelivery.ledger.entity;

import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.TransactionDirection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payout_lines")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutLine {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "payout_id", nullable = false)
    private UUID payoutId;

    @Column(name = "ledger_entry_id", nullable = false)
    private UUID ledgerEntryId;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private ChargeCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 6)
    private TransactionDirection direction;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "entry_created_at", nullable = false)
    private OffsetDateTime entryCreatedAt;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
