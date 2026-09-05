package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.PayoutLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PayoutLineRepository extends JpaRepository<PayoutLine, UUID> {
    List<PayoutLine> findByPayoutId(UUID payoutId);
    List<PayoutLine> findByLedgerEntryIdIn(List<UUID> ledgerEntryIds);
}
