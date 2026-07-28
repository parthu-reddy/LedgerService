package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.domain.Specification;
import java.util.UUID;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.TransactionDirection;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class LedgerEntrySpecification {

    public static Specification<LedgerEntry> filterBy(
            UUID transactionId,
            UUID accountId,
            ChargeCategory category,
            TransactionDirection direction) {
        
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (transactionId != null) {
                predicates.add(criteriaBuilder.equal(root.get("transactionId"), transactionId));
            }
            if (accountId != null) {
                predicates.add(criteriaBuilder.equal(root.get("accountId"), accountId));
            }
            if (category != null) {
                predicates.add(criteriaBuilder.equal(root.get("category"), category));
            }
            if (direction != null) {
                predicates.add(criteriaBuilder.equal(root.get("direction"), direction));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
