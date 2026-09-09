package com.fooddelivery.ledger.controller;

import com.fooddelivery.common.dto.PageResponseDto;
import com.fooddelivery.common.dto.ledger.CashRemittanceDto;
import com.fooddelivery.common.dto.ledger.CashSummaryDto;
import com.fooddelivery.ledger.entity.CashRemittance;
import com.fooddelivery.ledger.service.CashService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cash for the SERVICE identity. CustomerApplication builds the rider money screen from this;
 * it previously called the ADMIN-only cash endpoint and got a 403, so cash in hand was always zero.
 */
@RestController
@RequestMapping("/api/v1/internal/ledger/cash")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
public class InternalCashController {

    private final CashService cashService;

    @GetMapping("/drivers/{driverId}/summary")
    public ResponseEntity<CashSummaryDto> getCashSummary(@PathVariable UUID driverId) {
        return ResponseEntity.ok(cashService.getCashSummary(driverId));
    }

    @GetMapping("/drivers/{driverId}")
    public ResponseEntity<PageResponseDto<CashRemittanceDto>> getCashByDriver(
            @PathVariable UUID driverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CashRemittance> remittances = cashService.getCashRemittancesByDriver(driverId, PageRequest.of(page, size));
        return ResponseEntity.ok(PageResponseDto.<CashRemittanceDto>builder()
                .content(remittances.getContent().stream().map(c -> CashRemittanceDto.builder()
                        .id(c.getId())
                        .driverId(c.getDriverId())
                        .amount(c.getAmount())
                        .reference(c.getReference())
                        .recordedBy(c.getRecordedBy())
                        .ledgerTransactionId(c.getLedgerTransactionId())
                        .createdAt(c.getCreatedAt())
                        .build()).collect(Collectors.toList()))
                .number(remittances.getNumber())
                .size(remittances.getSize())
                .totalElements(remittances.getTotalElements())
                .totalPages(remittances.getTotalPages())
                .last(remittances.isLast())
                .first(remittances.isFirst())
                .numberOfElements(remittances.getNumberOfElements())
                .empty(remittances.isEmpty())
                .build());
    }
}
