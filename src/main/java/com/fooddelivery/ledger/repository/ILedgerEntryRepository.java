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
    List<LedgerEntry> findUnsettledEntries(@Param("accountId") UUID accountId, @Param("periodTo") java.time.Instant periodTo);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e, com.fooddelivery.ledger.entity.LedgerAccount a "
            + "WHERE a.id = e.accountId "
            + "AND a.ownerType = :ownerType "
            + "AND e.direction = :direction "
            + "AND e.createdAt >= :from AND e.createdAt < :to")
    java.math.BigDecimal sumByOwnerTypeAndDirectionInWindow(
            @Param("ownerType") com.fooddelivery.common.enums.LedgerAccountType ownerType,
            @Param("direction") com.fooddelivery.common.enums.TransactionDirection direction,
            @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e "
            + "WHERE e.accountId = :accountId AND e.direction = :direction AND e.category = :category")
    java.math.BigDecimal sumByAccountAndDirectionAndCategory(
            @Param("accountId") UUID accountId,
            @Param("direction") com.fooddelivery.common.enums.TransactionDirection direction,
            @Param("category") com.fooddelivery.common.enums.ChargeCategory category);

    /** One owner's movement under one category in {@code [from, to)}: an outlet's clawbacks this month, say. */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e, com.fooddelivery.ledger.entity.LedgerAccount a "
            + "WHERE a.id = e.accountId "
            + "AND a.ownerId = :ownerId AND a.ownerType = :ownerType "
            + "AND e.direction = :direction AND e.category = :category "
            + "AND e.createdAt >= :from AND e.createdAt < :to")
    java.math.BigDecimal sumByOwnerAndDirectionAndCategoryInWindow(
            @Param("ownerId") UUID ownerId,
            @Param("ownerType") com.fooddelivery.common.enums.LedgerAccountType ownerType,
            @Param("direction") com.fooddelivery.common.enums.TransactionDirection direction,
            @Param("category") com.fooddelivery.common.enums.ChargeCategory category,
            @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    /** Same-day movement on one account, used by the per-gateway reconciliation check. */
    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e, com.fooddelivery.ledger.entity.LedgerAccount a "
            + "WHERE a.id = e.accountId "
            + "AND a.ownerId = :ownerId AND a.ownerType = :ownerType "
            + "AND e.direction = :direction "
            + "AND e.createdAt >= :from AND e.createdAt < :to")
    java.math.BigDecimal sumByOwnerAndDirectionInWindow(
            @Param("ownerId") UUID ownerId,
            @Param("ownerType") com.fooddelivery.common.enums.LedgerAccountType ownerType,
            @Param("direction") com.fooddelivery.common.enums.TransactionDirection direction,
            @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.direction = com.fooddelivery.common.enums.TransactionDirection.DEBIT AND e.createdAt >= :from AND e.createdAt < :to")
    java.math.BigDecimal sumTotalDebitsInWindow(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM LedgerEntry e WHERE e.direction = com.fooddelivery.common.enums.TransactionDirection.CREDIT AND e.createdAt >= :from AND e.createdAt < :to")
    java.math.BigDecimal sumTotalCreditsInWindow(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);
    @org.springframework.data.jpa.repository.Query("SELECT e.transactionId FROM LedgerEntry e WHERE e.createdAt >= :from AND e.createdAt < :to GROUP BY e.transactionId HAVING SUM(CASE WHEN e.direction = com.fooddelivery.common.enums.TransactionDirection.DEBIT THEN e.amount ELSE 0 END) <> SUM(CASE WHEN e.direction = com.fooddelivery.common.enums.TransactionDirection.CREDIT THEN e.amount ELSE 0 END)")
    java.util.List<UUID> findUnbalancedTransactionsInWindow(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);
}
