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

    /**
     * Sums entry amounts for one reference, restricted to a direction and to the owner type of the
     * account the entry sits against. Deliberately generic: the choice of which direction and which
     * owner type constitute an "order total" is policy and lives in DoubleEntryLedgerService.
     *
     * Joins on accountId because LedgerEntry stores the account as a bare UUID, not a relation.
     */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e, com.fooddelivery.ledger.entity.LedgerAccount a "
            + "WHERE a.id = e.accountId "
            + "AND e.referenceId = :referenceId "
            + "AND e.direction = :direction "
            + "AND a.ownerType = :ownerType")
    java.math.BigDecimal sumByReferenceIdAndDirectionAndOwnerType(
            @Param("referenceId") UUID referenceId,
            @Param("direction") com.fooddelivery.common.enums.TransactionDirection direction,
            @Param("ownerType") com.fooddelivery.common.enums.AccountType ownerType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM LedgerEntry e WHERE e.transactionId = :transactionId")
    java.util.List<LedgerEntry> findByTransactionIdForUpdate(@Param("transactionId") UUID transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM LedgerEntry e WHERE e.referenceId = :referenceId")
    java.util.List<LedgerEntry> findByReferenceIdForUpdate(@Param("referenceId") UUID referenceId);
}
