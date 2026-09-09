package com.fooddelivery.ledger.controller;

import com.fooddelivery.common.dto.PageResponseDto;
import com.fooddelivery.common.dto.ledger.PayoutDto;
import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.mapper.PayoutMapper;
import com.fooddelivery.ledger.service.PayoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/internal/ledger/payouts")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
public class InternalPayoutController {

    private final PayoutService payoutService;

    @GetMapping
    public ResponseEntity<PageResponseDto<PayoutDto>> getPayouts(
            @RequestParam String payeeType,
            @RequestParam UUID payeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
            
        Page<Payout> payoutPage = payoutService.getPayouts(payeeType, payeeId, PageRequest.of(page, size));
        
        PageResponseDto<PayoutDto> response = PageResponseDto.<PayoutDto>builder()
                .content(payoutPage.getContent().stream().map(PayoutMapper::toDto).collect(Collectors.toList()))
                .number(payoutPage.getNumber())
                .size(payoutPage.getSize())
                .totalElements(payoutPage.getTotalElements())
                .totalPages(payoutPage.getTotalPages())
                .last(payoutPage.isLast())
                .first(payoutPage.isFirst())
                .numberOfElements(payoutPage.getNumberOfElements())
                .empty(payoutPage.isEmpty())
                .build();
        return ResponseEntity.ok(response);
    }

    /**
     * What one payee is owed and when they were last paid.
     *
     * <p>The restaurant and rider summary cards are built from this. They used to call the admin-only
     * pending queue for the whole platform with a SERVICE token: a 403 that was swallowed, leaving
     * every card showing a hardcoded zero, and a cross-tenant read if it had ever succeeded.
     */
    @GetMapping("/latest/{payeeType}/{payeeId}")
    public ResponseEntity<com.fooddelivery.common.dto.ledger.PayeeMoneySummaryDto> getPayeeSummary(
            @PathVariable String payeeType,
            @PathVariable UUID payeeId) {
        return ResponseEntity.ok(payoutService.payeeSummary(payeeType, payeeId));
    }
}
