package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ILedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>, JpaSpecificationExecutor<LedgerEntry> {
    boolean existsByTransactionId(UUID transactionId);
    
    java.util.List<LedgerEntry> findByTransactionId(UUID transactionId);

    java.util.List<LedgerEntry> findByTransactionIdIn(java.util.List<UUID> transactionIds);

    java.util.List<LedgerEntry> findByReferenceId(UUID referenceId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM LedgerEntry e WHERE e.transactionId = :transactionId")
    java.util.List<LedgerEntry> findByTransactionIdForUpdate(@Param("transactionId") UUID transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM LedgerEntry e WHERE e.referenceId = :referenceId")
    java.util.List<LedgerEntry> findByReferenceIdForUpdate(@Param("referenceId") UUID referenceId);
}
