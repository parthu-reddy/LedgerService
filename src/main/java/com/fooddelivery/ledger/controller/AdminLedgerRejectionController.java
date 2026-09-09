package com.fooddelivery.ledger.controller;

import com.fooddelivery.common.dto.PageResponseDto;
import com.fooddelivery.ledger.dto.LedgerRejectionDto;
import com.fooddelivery.ledger.entity.LedgerRejection;
import com.fooddelivery.ledger.repository.ILedgerRejectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The admin surface for ledger rejections.
 *
 * <p>A rejection is a money movement the ledger refused: a transaction id it could not re-derive,
 * an unbalanced leg, an underfunded transfer, or an event that exhausted its retries. Every one of
 * them means an order's money was never booked.
 *
 * <p>Nothing could read this table. It is written by {@code LedgerEventListener} -- and since the
 * DLT path was changed to record a rejection instead of publishing to a topic nobody consumed, it is
 * now the *only* place a lost ledger event surfaces. The {@code resolved_at}, {@code resolved_by} and
 * {@code resolution_note} columns existed from the first migration and nothing could ever set them,
 * so {@code checkStuck}'s STUCK break could be raised and never cleared.
 */
@RestController
@RequestMapping("/api/v1/internal/admin/ledger/rejections")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminLedgerRejectionController {

    private final ILedgerRejectionRepository rejectionRepository;

    @GetMapping
    public ResponseEntity<PageResponseDto<LedgerRejectionDto>> list(
            @RequestParam(defaultValue = "false") boolean resolved,
            @RequestParam(required = false) String producer,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size);
        Page<LedgerRejection> rejections;
        if (resolved) {
            rejections = rejectionRepository.findByResolvedAtIsNotNullOrderByResolvedAtDesc(pageable);
        } else if (producer != null && !producer.isBlank()) {
            rejections = rejectionRepository.findByProducerAndResolvedAtIsNullOrderByCreatedAtAsc(producer, pageable);
        } else {
            rejections = rejectionRepository.findByResolvedAtIsNullOrderByCreatedAtAsc(pageable);
        }

        return ResponseEntity.ok(PageResponseDto.<LedgerRejectionDto>builder()
                .content(rejections.getContent().stream().map(AdminLedgerRejectionController::toDto).collect(Collectors.toList()))
                .number(rejections.getNumber())
                .size(rejections.getSize())
                .totalElements(rejections.getTotalElements())
                .totalPages(rejections.getTotalPages())
                .last(rejections.isLast())
                .first(rejections.isFirst())
                .numberOfElements(rejections.getNumberOfElements())
                .empty(rejections.isEmpty())
                .build());
    }

    @GetMapping("/count")
    public ResponseEntity<Long> unresolvedCount() {
        return ResponseEntity.ok(rejectionRepository.countByResolvedAtIsNull());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LedgerRejectionDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(toDto(rejectionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rejection not found: " + id))));
    }

    /**
     * Marks a rejection dealt with. This records a judgement; it does not replay the movement --
     * the producer owns that, and a replay carries the same deterministic transaction id, so the
     * ledger's own idempotency stops it being booked twice.
     */
    @PostMapping("/{id}/resolve")
    @Transactional
    public ResponseEntity<LedgerRejectionDto> resolve(@PathVariable UUID id, @RequestBody ResolveRequest request) {
        if (request == null || request.note() == null || request.note().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A resolution note is required: this is the audit record of why money that was "
                    + "refused no longer needs booking.");
        }
        LedgerRejection rejection = rejectionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rejection not found: " + id));

        if (rejection.getResolvedAt() != null) {
            // Idempotent: re-resolving must not overwrite who first signed it off.
            return ResponseEntity.ok(toDto(rejection));
        }

        rejection.setResolvedAt(OffsetDateTime.now());
        rejection.setResolvedBy(currentAdmin());
        rejection.setResolutionNote(request.note());
        rejectionRepository.save(rejection);
        log.info("Ledger rejection {} resolved by {}: {}", id, rejection.getResolvedBy(), request.note());

        return ResponseEntity.ok(toDto(rejection));
    }

    private static String currentAdmin() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "unknown";
    }

    private static LedgerRejectionDto toDto(LedgerRejection r) {
        return LedgerRejectionDto.builder()
                .id(r.getId())
                .eventId(r.getEventId())
                .producer(r.getProducer())
                .reason(r.getReason())
                .payload(r.getPayload())
                .createdAt(r.getCreatedAt())
                .resolvedAt(r.getResolvedAt())
                .resolvedBy(r.getResolvedBy())
                .resolutionNote(r.getResolutionNote())
                .ageMinutes(r.getCreatedAt() == null ? null
                        : Duration.between(r.getCreatedAt(), OffsetDateTime.now()).toMinutes())
                .build();
    }

    public record ResolveRequest(String note) {}
}
