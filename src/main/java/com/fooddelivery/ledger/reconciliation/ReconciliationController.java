package com.fooddelivery.ledger.reconciliation;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/admin/ledger/reconciliation")
@RequiredArgsConstructor



public class ReconciliationController {

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationBreakRepository breakRepository;
    private final ReconciliationService reconciliationService;

    @GetMapping("/runs")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<ReconciliationRun> getRuns(Pageable pageable) {
        return runRepository.findAll(pageable);
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ReconciliationRun getRun(@PathVariable UUID id) {
        return runRepository.findById(id).orElseThrow();
    }

    @PostMapping("/runs/{id}/execute")
    @PreAuthorize("hasRole('ADMIN')")
    public ReconciliationRun executeRun(@PathVariable UUID id, @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        // According to original code, it triggers a run for a date. 
        // Here we can fetch the run if needed, but since the service takes a date, we just pass the date or a default date
        return reconciliationService.executeRun(date != null ? date : LocalDate.now());
    }

    @GetMapping("/runs/{id}/breaks")
    @PreAuthorize("hasRole('ADMIN')")
    public Page<ReconciliationBreak> getBreaks(
            @PathVariable UUID id,
            @RequestParam(required = false) BreakKind kind,
            @RequestParam(required = false) Boolean resolved,
            Pageable pageable) {
        if (kind != null && resolved != null) {
            return resolved ? breakRepository.findByKindAndResolvedAtIsNotNull(kind, pageable)
                            : breakRepository.findByKindAndResolvedAtIsNull(kind, pageable);
        } else if (kind != null) {
            return breakRepository.findByKind(kind, pageable);
        } else if (resolved != null) {
            return resolved ? breakRepository.findByResolvedAtIsNotNull(pageable)
                            : breakRepository.findByResolvedAtIsNull(pageable);
        }
        return breakRepository.findAll(pageable);
    }

    @PostMapping("/runs/{id}/resolve-breaks")
    @PreAuthorize("hasRole('ADMIN')")
    public void resolveBreak(@PathVariable UUID id, @RequestBody ResolveBreakRequest request) {
        ReconciliationBreak rBreak = breakRepository.findById(id).orElseThrow();
        rBreak.setResolvedAt(java.time.OffsetDateTime.now());
        rBreak.setResolvedBy(request.getResolvedBy());
        rBreak.setNote(request.getNote());
        breakRepository.save(rBreak);
    }
}

class ResolveBreakRequest {
    private UUID resolvedBy;
    private String note;
    
    public UUID getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(UUID resolvedBy) { this.resolvedBy = resolvedBy; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
