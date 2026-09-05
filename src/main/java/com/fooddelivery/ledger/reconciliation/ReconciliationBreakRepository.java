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

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(b) > 0 FROM ReconciliationBreak b JOIN ReconciliationRun r ON b.runId = r.id WHERE b.kind = :kind AND b.subjectId = :subjectId AND CAST(r.startedAt AS date) = CURRENT_DATE")
    boolean existsByKindAndSubjectIdCreatedToday(@org.springframework.data.repository.query.Param("kind") BreakKind kind, @org.springframework.data.repository.query.Param("subjectId") UUID subjectId);

    org.springframework.data.domain.Page<ReconciliationBreak> findByKind(BreakKind kind, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ReconciliationBreak> findByResolvedAtIsNull(org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ReconciliationBreak> findByResolvedAtIsNotNull(org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ReconciliationBreak> findByKindAndResolvedAtIsNull(BreakKind kind, org.springframework.data.domain.Pageable pageable);
    org.springframework.data.domain.Page<ReconciliationBreak> findByKindAndResolvedAtIsNotNull(BreakKind kind, org.springframework.data.domain.Pageable pageable);
}
