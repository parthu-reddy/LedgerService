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
}
