package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.PayoutOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutOperationRepository extends JpaRepository<PayoutOperation, UUID> {
    Optional<PayoutOperation> findByIdempotencyKey(String idempotencyKey);

    /**
     * Claims an operation key without ever raising a uniqueness exception.
     *
     * <p>A payout row lock serializes actions for one payout, but an idempotency key is deliberately
     * global. Two requests using one key for different payouts can therefore reach this insert while
     * holding different payout locks. PostgreSQL {@code ON CONFLICT DO NOTHING} leaves the losing
     * transaction usable so the service can read the winning operation and return a controlled
     * replay or conflict instead of a 5xx.
     *
     * @return 1 when this transaction claimed the key; 0 when it was already claimed.
     */
    @Transactional
    @Modifying
    @Query(value = "INSERT INTO payout_operations "
            + "(id, payout_id, action, idempotency_key, request_hash, actor_id, status_before, status_after, "
            + "outcome, bank_reference, failure_reason, ledger_transaction_id, created_at) "
            + "VALUES (:id, :payoutId, :action, :idempotencyKey, :requestHash, :actorId, :statusBefore, :statusAfter, "
            + ":outcome, :bankReference, :failureReason, :ledgerTransactionId, NOW()) "
            + "ON CONFLICT (idempotency_key) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id,
                       @Param("payoutId") UUID payoutId,
                       @Param("action") String action,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("requestHash") String requestHash,
                       @Param("actorId") UUID actorId,
                       @Param("statusBefore") String statusBefore,
                       @Param("statusAfter") String statusAfter,
                       @Param("outcome") String outcome,
                       @Param("bankReference") String bankReference,
                       @Param("failureReason") String failureReason,
                       @Param("ledgerTransactionId") UUID ledgerTransactionId);
}
