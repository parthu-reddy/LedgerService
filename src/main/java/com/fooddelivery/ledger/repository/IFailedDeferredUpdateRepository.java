package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.FailedDeferredUpdate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IFailedDeferredUpdateRepository extends JpaRepository<FailedDeferredUpdate, UUID> {
}
