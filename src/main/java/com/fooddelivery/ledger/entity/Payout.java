package com.fooddelivery.ledger.entity;

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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payouts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payout {
    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "payee_type", nullable = false, length = 32)
    private String payeeType;

    @Column(name = "payee_id", nullable = false)
    private UUID payeeId;

    @Column(name = "payee_display_name", nullable = false, length = 255)
    private String payeeDisplayName;

    @Column(name = "period_from", nullable = false)
    private OffsetDateTime periodFrom;

    @Column(name = "period_to", nullable = false)
    private OffsetDateTime periodTo;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @Builder.Default
        @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.CHAR)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PayoutStatus status;

    @Column(name = "beneficiary_snapshot", nullable = false, columnDefinition = "VARCHAR(2000)")
    private String beneficiarySnapshot;

    @Column(name = "bank_reference", length = 128)
    private String bankReference;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "paid_by")
    private UUID paidBy;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Column(name = "ledger_transaction_id", nullable = false)
    private UUID ledgerTransactionId;

    @Column(name = "settled_transaction_id")
    private UUID settledTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "approved_at")
    private OffsetDateTime approvedAt;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
