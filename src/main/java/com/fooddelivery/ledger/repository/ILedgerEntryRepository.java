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
import java.util.List;

@Repository
public interface ILedgerEntryRepository extends JpaRepository<LedgerEntry, UUID>, JpaSpecificationExecutor<LedgerEntry> {
    boolean existsByTransactionId(UUID transactionId);
    
    List<LedgerEntry> findByTransactionId(UUID transactionId);

    List<LedgerEntry> findByTransactionIdIn(List<UUID> transactionIds);

    List<LedgerEntry> findByReferenceId(UUID referenceId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e, com.fooddelivery.ledger.entity.LedgerAccount a "
            + "WHERE a.id = e.accountId "
            + "AND e.referenceId = :referenceId "
            + "AND e.direction = :direction "
            + "AND a.ownerType = :ownerType")
    java.math.BigDecimal sumByReferenceIdAndDirectionAndOwnerType(
            @Param("referenceId") UUID referenceId,
            @Param("direction") com.fooddelivery.common.enums.TransactionDirection direction,
            @Param("ownerType") com.fooddelivery.common.enums.LedgerAccountType ownerType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM LedgerEntry e WHERE e.transactionId = :transactionId")
    List<LedgerEntry> findByTransactionIdForUpdate(@Param("transactionId") UUID transactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM LedgerEntry e WHERE e.referenceId = :referenceId")
    List<LedgerEntry> findByReferenceIdForUpdate(@Param("referenceId") UUID referenceId);

    @Query("SELECT e FROM LedgerEntry e WHERE e.accountId = :accountId AND e.createdAt <= :periodTo " +
           "AND NOT EXISTS (SELECT 1 FROM PayoutLine pl WHERE pl.ledgerEntryId = e.id AND pl.active = true)")
    List<LedgerEntry> findUnsettledEntries(@Param("accountId") UUID accountId, @Param("periodTo") java.time.OffsetDateTime periodTo);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e, com.fooddelivery.ledger.entity.LedgerAccount a "
            + "WHERE a.id = e.accountId "
            + "AND a.ownerType = :ownerType "
            + "AND e.direction = :direction "
            + "AND CAST(e.createdAt AS date) = :date")
    java.math.BigDecimal sumByOwnerTypeAndDirectionAndDate(
            @Param("ownerType") com.fooddelivery.common.enums.LedgerAccountType ownerType,
            @Param("direction") com.fooddelivery.common.enums.TransactionDirection direction,
            @Param("date") java.time.LocalDate date);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.direction = com.fooddelivery.common.enums.TransactionDirection.DEBIT AND CAST(e.createdAt AS date) = :date")
    java.math.BigDecimal sumTotalDebitsByDate(@Param("date") java.time.LocalDate date);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.direction = com.fooddelivery.common.enums.TransactionDirection.CREDIT AND CAST(e.createdAt AS date) = :date")
    java.math.BigDecimal sumTotalCreditsByDate(@Param("date") java.time.LocalDate date);
    @org.springframework.data.jpa.repository.Query("SELECT e.transactionId FROM LedgerEntry e WHERE CAST(e.createdAt AS date) = :date GROUP BY e.transactionId HAVING SUM(CASE WHEN e.direction = com.fooddelivery.common.enums.TransactionDirection.DEBIT THEN e.amount ELSE 0 END) <> SUM(CASE WHEN e.direction = com.fooddelivery.common.enums.TransactionDirection.CREDIT THEN e.amount ELSE 0 END)")
    java.util.List<UUID> findUnbalancedTransactionsByDate(@org.springframework.data.repository.query.Param("date") java.time.LocalDate date);
}
