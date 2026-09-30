package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.entity.PayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, UUID> {
    Optional<Payout> findByIdempotencyKey(String idempotencyKey);

    /**
     * Financial lifecycle actions must observe one state at a time. Locking the payout before the
     * state check prevents a paid and failed action from both posting their distinct ledger legs.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payout p WHERE p.id = :payoutId")
    Optional<Payout> findByIdForUpdate(@Param("payoutId") UUID payoutId);

    List<Payout> findByPayeeTypeAndPayeeIdAndStatusIn(String payeeType, UUID payeeId, List<PayoutStatus> statuses);
    org.springframework.data.domain.Page<Payout> findByPayeeTypeAndPayeeId(String payeeType, UUID payeeId, org.springframework.data.domain.Pageable pageable);
    Optional<Payout> findFirstByPayeeTypeAndPayeeIdAndStatusOrderByPaidAtDesc(String payeeType, UUID payeeId, PayoutStatus status);

    /** Oldest payout still sitting in a status, used by the PayoutApprovedNotPaid alert. */
    Optional<Payout> findFirstByStatusOrderByApprovedAtAsc(PayoutStatus status);
}
