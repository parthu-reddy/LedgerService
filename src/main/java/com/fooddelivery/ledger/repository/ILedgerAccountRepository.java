package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.LedgerAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import com.fooddelivery.common.enums.AccountType;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ILedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {
    @Lock(LockModeType.OPTIMISTIC)
    Optional<LedgerAccount> findByOwnerIdAndOwnerType(UUID ownerId, AccountType ownerType);

    java.util.List<LedgerAccount> findByOwnerTypeInAndBalanceGreaterThan(java.util.List<AccountType> ownerTypes, java.math.BigDecimal balance);
}
