package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.LedgerAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import com.fooddelivery.common.enums.LedgerAccountType;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface ILedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    Optional<LedgerAccount> findByOwnerIdAndOwnerType(UUID ownerId, LedgerAccountType ownerType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT a FROM LedgerAccount a WHERE a.id = :id")
    Optional<LedgerAccount> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT a FROM LedgerAccount a WHERE a.ownerId = :ownerId AND a.ownerType = :ownerType")
    Optional<LedgerAccount> findByOwnerIdAndOwnerTypeForUpdate(@org.springframework.data.repository.query.Param("ownerId") UUID ownerId, @org.springframework.data.repository.query.Param("ownerType") LedgerAccountType ownerType);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE LedgerAccount a SET a.balance = a.balance + :amount, a.lockVersion = a.lockVersion + 1 WHERE a.id = :id")
    int updateBalance(@org.springframework.data.repository.query.Param("id") UUID id, @org.springframework.data.repository.query.Param("amount") java.math.BigDecimal amount);

    List<LedgerAccount> findByOwnerTypeInAndBalanceGreaterThan(List<LedgerAccountType> ownerTypes, java.math.BigDecimal balance);

    /**
     * Creates the account only if (owner_id, owner_type) is not taken, and never raises.
     *
     * <p>The previous code let {@code saveAndFlush} throw {@code DataIntegrityViolationException} on
     * a concurrent first write and then re-queried inside the same transaction. Postgres marks a
     * transaction aborted the moment a constraint fires, so that re-query fails with
     * "current transaction is aborted" -- the recovery could not run. {@code ON CONFLICT DO NOTHING}
     * makes the insert a no-op instead of an error, leaving the transaction usable.
     *
     * @return 1 when this call created the row, 0 when another transaction already had.
     */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value =
            "INSERT INTO ledger_accounts (id, owner_id, owner_type, kind, balance, currency, lock_version, created_at) "
            + "VALUES (:id, :ownerId, :ownerType, :kind, 0, :currency, 0, NOW()) "
            + "ON CONFLICT (owner_type, owner_id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@org.springframework.data.repository.query.Param("id") UUID id,
                       @org.springframework.data.repository.query.Param("ownerId") UUID ownerId,
                       @org.springframework.data.repository.query.Param("ownerType") String ownerType,
                       @org.springframework.data.repository.query.Param("kind") String kind,
                       @org.springframework.data.repository.query.Param("currency") String currency);
}
