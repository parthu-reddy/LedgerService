package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.entity.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, UUID> {
    Optional<Payout> findByIdempotencyKey(String idempotencyKey);
    List<Payout> findByPayeeTypeAndPayeeIdAndStatusIn(String payeeType, UUID payeeId, List<PayoutStatus> statuses);
    org.springframework.data.domain.Page<Payout> findByPayeeTypeAndPayeeId(String payeeType, UUID payeeId, org.springframework.data.domain.Pageable pageable);
}
