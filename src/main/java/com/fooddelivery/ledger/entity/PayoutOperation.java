package com.fooddelivery.ledger.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit and idempotency record for a payout lifecycle action.
 *
 * <p>The table is deliberately append-only. It binds an idempotency key to the exact payout,
 * action, actor, and normalized request hash that first used it. A retry with that same request
 * can therefore be acknowledged without creating another ledger movement; a changed request is a
 * conflict instead of an ambiguous second operator action.
 */
@Entity
@Table(
        name = "payout_operations",
        uniqueConstraints = @UniqueConstraint(name = "uq_payout_operation_idempotency_key", columnNames = "idempotency_key"),
        indexes = @Index(name = "idx_payout_operations_payout_created", columnList = "payout_id, created_at")
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PayoutOperation {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payout_id", nullable = false, updatable = false)
    private UUID payoutId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 16, updatable = false)
    private PayoutOperationAction action;

    @Column(name = "idempotency_key", nullable = false, length = 255, updatable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before", nullable = false, length = 16, updatable = false)
    private PayoutStatus statusBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_after", nullable = false, length = 16, updatable = false)
    private PayoutStatus statusAfter;

    /** APPLIED is recorded only when the transaction commits with the lifecycle action. */
    @Column(name = "outcome", nullable = false, length = 32, updatable = false)
    private String outcome;

    @Column(name = "bank_reference", length = 128, updatable = false)
    private String bankReference;

    @Column(name = "failure_reason", length = 1000, updatable = false)
    private String failureReason;

    @Column(name = "ledger_transaction_id", updatable = false)
    private UUID ledgerTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
