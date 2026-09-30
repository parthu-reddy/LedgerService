package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.LedgerRejection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface ILedgerRejectionRepository extends JpaRepository<LedgerRejection, UUID> {

    long countByResolvedAtIsNullAndCreatedAtBefore(java.time.Instant date);

    /** The queue an operator works: a rejected movement is money that was never booked. */
    Page<LedgerRejection> findByResolvedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

    Page<LedgerRejection> findByResolvedAtIsNotNullOrderByResolvedAtDesc(Pageable pageable);

    Page<LedgerRejection> findByProducerAndResolvedAtIsNullOrderByCreatedAtAsc(String producer, Pageable pageable);

    long countByResolvedAtIsNull();

    /**
     * Records the first resolution only.
     *
     * <p>The resolved fields are financial-audit evidence. An ordinary read-check-save lets two
     * administrators both observe an unresolved row and lets the later commit overwrite the first
     * sign-off. The {@code resolvedAt IS NULL} predicate is the compare-and-set: PostgreSQL locks
     * the row while evaluating it, so exactly one writer can change it from unresolved.
     *
     * @return {@code 1} when this caller recorded the decision, or {@code 0} when the row was
     * already resolved (or does not exist)
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE LedgerRejection r "
            + "SET r.resolvedAt = :resolvedAt, r.resolvedBy = :resolvedBy, r.resolutionNote = :resolutionNote "
            + "WHERE r.id = :id AND r.resolvedAt IS NULL")
    int resolveIfUnresolved(@Param("id") UUID id,
                            @Param("resolvedAt") Instant resolvedAt,
                            @Param("resolvedBy") String resolvedBy,
                            @Param("resolutionNote") String resolutionNote);
}
