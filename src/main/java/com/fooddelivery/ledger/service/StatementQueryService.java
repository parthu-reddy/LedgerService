package com.fooddelivery.ledger.service;

import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.dto.ledger.LedgerStatementLineDto;
import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.entity.PayoutStatus;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StatementQueryService {

    private final EntityManager entityManager;
    private final ILedgerAccountRepository accountRepository;

    @Transactional(readOnly = true)
    public Page<LedgerStatementLineDto> getStatement(LedgerAccountType ownerType, UUID ownerId, 
                                                     OffsetDateTime from, OffsetDateTime to, 
                                                     Boolean settled, Pageable pageable) {
        
        LedgerAccount account = accountRepository.findByOwnerIdAndOwnerType(ownerId, ownerType).orElse(null);
        if (account == null) {
            return Page.empty(pageable);
        }

        StringBuilder sql = new StringBuilder("""
            SELECT e.transaction_id, e.reference_id, e.category, e.amount, e.direction, e.created_at, e.description,
                   pl.payout_id, p.status as payout_status
            FROM ledger_entries e
            LEFT JOIN payout_lines pl ON e.id = pl.ledger_entry_id AND pl.active = true
            LEFT JOIN payouts p ON pl.payout_id = p.id
            WHERE e.account_id = :accountId
        """);

        if (from != null) sql.append(" AND e.created_at >= :from");
        if (to != null) sql.append(" AND e.created_at <= :to");
        if (settled != null) {
            if (settled) {
                sql.append(" AND pl.payout_id IS NOT NULL AND p.status IN ('PAID', 'SETTLED', 'COMPLETED')");
            } else {
                sql.append(" AND (pl.payout_id IS NULL OR p.status NOT IN ('PAID', 'SETTLED', 'COMPLETED'))");
            }
        }

        String countSql = "SELECT COUNT(*) FROM (" + sql.toString() + ") AS c";
        sql.append(" ORDER BY e.created_at DESC");

        Query countQuery = entityManager.createNativeQuery(countSql);
        countQuery.setParameter("accountId", account.getId());
        if (from != null) countQuery.setParameter("from", from);
        if (to != null) countQuery.setParameter("to", to);
        
        Number totalElements = (Number) countQuery.getSingleResult();

        Query dataQuery = entityManager.createNativeQuery(sql.toString());
        dataQuery.setParameter("accountId", account.getId());
        if (from != null) dataQuery.setParameter("from", from);
        if (to != null) dataQuery.setParameter("to", to);

        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        List<Object[]> results = dataQuery.getResultList();
        List<LedgerStatementLineDto> dtos = new ArrayList<>();

        for (Object[] row : results) {
            UUID transactionId = (UUID) row[0];
            UUID referenceId = (UUID) row[1];
            String categoryStr = (String) row[2];
            BigDecimal amount = (BigDecimal) row[3];
            String directionStr = (String) row[4];
            OffsetDateTime createdAt = ((Timestamp) row[5]).toInstant().atOffset(ZoneOffset.UTC);
            String description = (String) row[6];
            UUID payoutId = (UUID) row[7];
            String payoutStatusStr = (String) row[8];

            boolean isSettled = false;
            PayoutStatus pStatus = null;
            if (payoutStatusStr != null) {
                pStatus = PayoutStatus.valueOf(payoutStatusStr);
                isSettled = (pStatus == PayoutStatus.PAID);
            }

            dtos.add(LedgerStatementLineDto.builder()
                .transactionId(transactionId)
                .referenceId(referenceId)
                .category(com.fooddelivery.common.enums.ChargeCategory.valueOf(categoryStr))
                .amount(amount)
                .direction(com.fooddelivery.common.enums.TransactionDirection.valueOf(directionStr))
                .createdAt(createdAt)
                .description(description)
                .payoutId(payoutId)
                .payoutStatus(pStatus != null ? pStatus.name() : null)
                .settled(isSettled)
                .build());
        }

        return new PageImpl<>(dtos, pageable, totalElements.longValue());
    }

    @Transactional(readOnly = true)
    public List<LedgerStatementLineDto> getStatementByReferenceId(UUID referenceId) {
        String sql = """
            SELECT e.transaction_id, e.reference_id, e.category, e.amount, e.direction, e.created_at, e.description,
                   pl.payout_id, p.status as payout_status
            FROM ledger_entries e
            LEFT JOIN payout_lines pl ON e.id = pl.ledger_entry_id AND pl.active = true
            LEFT JOIN payouts p ON pl.payout_id = p.id
            WHERE e.reference_id = :referenceId
            ORDER BY e.created_at ASC
        """;

        Query dataQuery = entityManager.createNativeQuery(sql);
        dataQuery.setParameter("referenceId", referenceId);

        List<Object[]> results = dataQuery.getResultList();
        List<LedgerStatementLineDto> dtos = new ArrayList<>();

        for (Object[] row : results) {
            UUID transactionId = (UUID) row[0];
            UUID refId = (UUID) row[1];
            String categoryStr = (String) row[2];
            BigDecimal amount = (BigDecimal) row[3];
            String directionStr = (String) row[4];
            OffsetDateTime createdAt = ((Timestamp) row[5]).toInstant().atOffset(ZoneOffset.UTC);
            String description = (String) row[6];
            UUID payoutId = (UUID) row[7];
            String payoutStatusStr = (String) row[8];

            boolean isSettled = false;
            PayoutStatus pStatus = null;
            if (payoutStatusStr != null) {
                pStatus = PayoutStatus.valueOf(payoutStatusStr);
                isSettled = (pStatus == PayoutStatus.PAID);
            }

            dtos.add(LedgerStatementLineDto.builder()
                .transactionId(transactionId)
                .referenceId(refId)
                .category(com.fooddelivery.common.enums.ChargeCategory.valueOf(categoryStr))
                .amount(amount)
                .direction(com.fooddelivery.common.enums.TransactionDirection.valueOf(directionStr))
                .createdAt(createdAt)
                .description(description)
                .payoutId(payoutId)
                .payoutStatus(pStatus != null ? pStatus.name() : null)
                .settled(isSettled)
                .build());
        }

        return dtos;
    }
}
