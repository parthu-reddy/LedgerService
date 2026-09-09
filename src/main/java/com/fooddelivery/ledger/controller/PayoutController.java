package com.fooddelivery.ledger.controller;

import com.fooddelivery.ledger.dto.CreatePayoutRequest;
import com.fooddelivery.ledger.dto.PendingPayoutResponse;
import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.service.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/admin/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PayoutController {

    private final PayoutService payoutService;
    private UUID getAdminId() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Payout> createPayout(
            @RequestBody CreatePayoutRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        Payout payout = payoutService.create(request, idempotencyKey, getAdminId());
        return ResponseEntity.ok(payout);
    }

    @PostMapping("/{payoutId}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> approvePayout(@PathVariable UUID payoutId) {
        payoutService.approve(payoutId, getAdminId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{payoutId}/mark-paid")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> markPayoutPaid(
            @PathVariable UUID payoutId,
            @RequestParam String bankReference) {
        payoutService.markPaid(payoutId, bankReference, getAdminId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{payoutId}/fail")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> failPayout(
            @PathVariable UUID payoutId,
            @RequestParam String reason) {
        payoutService.fail(payoutId, reason, getAdminId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{payoutId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> cancelPayout(@PathVariable UUID payoutId) {
        payoutService.cancel(payoutId, getAdminId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{payoutId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.fooddelivery.ledger.dto.PayoutDetailResponse> getPayoutDetail(@PathVariable UUID payoutId) {
        return ResponseEntity.ok(payoutService.getDetail(payoutId));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<PendingPayoutResponse>> getPendingPayouts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(payoutService.pending(page, size));
    }

    /**
     * Payout history for one payee. The admin drawer's "previous payouts" tab and the history screen
     * both call this; neither worked, because the client was generated against an endpoint that had
     * never been written and the controller answered 405 at this path.
     */
    @GetMapping
    public ResponseEntity<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.common.dto.ledger.PayoutDto>> getPayouts(
            @RequestParam String payeeType,
            @RequestParam UUID payeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        org.springframework.data.domain.Page<Payout> payoutPage =
                payoutService.getPayouts(payeeType, payeeId, org.springframework.data.domain.PageRequest.of(page, size));
        return ResponseEntity.ok(com.fooddelivery.common.dto.PageResponseDto.<com.fooddelivery.common.dto.ledger.PayoutDto>builder()
                .content(payoutPage.getContent().stream()
                        .map(com.fooddelivery.ledger.mapper.PayoutMapper::toDto)
                        .collect(java.util.stream.Collectors.toList()))
                .number(payoutPage.getNumber())
                .size(payoutPage.getSize())
                .totalElements(payoutPage.getTotalElements())
                .totalPages(payoutPage.getTotalPages())
                .last(payoutPage.isLast())
                .first(payoutPage.isFirst())
                .numberOfElements(payoutPage.getNumberOfElements())
                .empty(payoutPage.isEmpty())
                .build());
    }
}

