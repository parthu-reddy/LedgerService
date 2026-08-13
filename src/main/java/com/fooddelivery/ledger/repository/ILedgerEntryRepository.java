package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ILedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>, JpaSpecificationExecutor<LedgerEntry> {
    boolean existsByTransactionId(UUID transactionId);
    
    java.util.List<LedgerEntry> findByTransactionId(UUID transactionId);

    java.util.List<LedgerEntry> findByTransactionIdIn(java.util.List<UUID> transactionIds);

    java.util.List<LedgerEntry> findByReferenceId(UUID referenceId);
}
