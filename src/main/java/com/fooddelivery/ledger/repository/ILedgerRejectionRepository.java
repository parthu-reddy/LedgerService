package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.LedgerRejection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ILedgerRejectionRepository extends JpaRepository<LedgerRejection, UUID> {

    long countByResolvedAtIsNullAndCreatedAtBefore(java.time.OffsetDateTime date);

    /** The queue an operator works: a rejected movement is money that was never booked. */
    Page<LedgerRejection> findByResolvedAtIsNullOrderByCreatedAtAsc(Pageable pageable);

    Page<LedgerRejection> findByResolvedAtIsNotNullOrderByResolvedAtDesc(Pageable pageable);

    Page<LedgerRejection> findByProducerAndResolvedAtIsNullOrderByCreatedAtAsc(String producer, Pageable pageable);

    long countByResolvedAtIsNull();
}
