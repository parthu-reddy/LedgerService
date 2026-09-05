package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.LedgerRejection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ILedgerRejectionRepository extends JpaRepository<LedgerRejection, UUID> {
}
