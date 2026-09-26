package com.fooddelivery.ledger.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.List;

@Repository
public interface ReconciliationBreakRepository extends JpaRepository<ReconciliationBreak, UUID>, JpaSpecificationExecutor<ReconciliationBreak> {
    List<ReconciliationBreak> findByKindAndSubjectTypeAndSubjectId(BreakKind kind, String subjectType, UUID subjectId);
    boolean existsByKindAndSubjectIdAndResolvedAtIsNull(BreakKind kind, UUID subjectId);

    /** Drives the alert gauges: read from the table so resolving a break lowers the series again. */
    long countByResolvedAtIsNull();

    long countByKindAndResolvedAtIsNull(BreakKind kind);

    /** Whether a run that started within {@code [from, to)} already recorded this break: today's, in the accounting zone. */
    @org.springframework.data.jpa.repository.Query("SELECT COUNT(b) > 0 FROM ReconciliationBreak b JOIN ReconciliationRun r ON b.runId = r.id WHERE b.kind = :kind AND b.subjectId = :subjectId AND r.startedAt >= :from AND r.startedAt < :to")
    boolean existsByKindAndSubjectIdStartedWithin(@org.springframework.data.repository.query.Param("kind") BreakKind kind, @org.springframework.data.repository.query.Param("subjectId") UUID subjectId,
            @org.springframework.data.repository.query.Param("from") java.time.Instant from, @org.springframework.data.repository.query.Param("to") java.time.Instant to);

    org.springframework.data.domain.Page<ReconciliationBreak> findByKind(BreakKind kind, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ReconciliationBreak> findByResolvedAtIsNull(org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ReconciliationBreak> findByResolvedAtIsNotNull(org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ReconciliationBreak> findByKindAndResolvedAtIsNull(BreakKind kind, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ReconciliationBreak> findByKindAndResolvedAtIsNotNull(BreakKind kind, org.springframework.data.domain.Pageable pageable);
}
