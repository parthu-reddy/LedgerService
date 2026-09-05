package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.CashRemittance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CashRemittanceRepository extends JpaRepository<CashRemittance, UUID> {
    List<CashRemittance> findByDriverId(UUID driverId);
    org.springframework.data.domain.Page<CashRemittance> findByDriverId(UUID driverId, org.springframework.data.domain.Pageable pageable);
}
