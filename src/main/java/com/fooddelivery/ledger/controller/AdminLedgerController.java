package com.fooddelivery.ledger.controller;

import com.fooddelivery.common.dto.ApiResponse;
import com.fooddelivery.common.enums.LedgerAccountType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The admin ledger explorer.
 *
 * <p>These two endpoints used to hang off {@code LedgerController} at
 * {@code /api/v1/ledger/admin/**}. The gateway routes {@code /api/v1/ledger/statements/**},
 * {@code /payouts/**} and {@code /cash/**}, and separately {@code /api/v1/internal/admin/ledger/**}
 * -- but nothing matched {@code /api/v1/ledger/admin/**}, so every request the admin ledger screen
 * made died at the gateway with a 404. Found 2026-09-09 while writing the Phase 6 UI tests.
 *
 * <p>They now sit under the same {@code /api/v1/internal/admin/ledger} prefix as the rejection
 * queue, which the {@code admin-ledger-routes} route already carries and rate-limits.
 */
@RestController
@RequestMapping("/api/v1/internal/admin/ledger")
@RequiredArgsConstructor
public class AdminLedgerController {

    private final com.fooddelivery.ledger.service.DoubleEntryLedgerService ledgerService;

    @PreAuthorize("hasRole(\'ADMIN\')")
    @GetMapping("/entries")
    public ResponseEntity<ApiResponse<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.ledger.entity.LedgerEntry>>> getEntries(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) UUID transactionId, @RequestParam(required = false) UUID ownerId, @RequestParam(required = false) LedgerAccountType ownerType, @RequestParam(required = false) com.fooddelivery.common.enums.ChargeCategory category, @RequestParam(required = false) com.fooddelivery.common.enums.TransactionDirection direction) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        org.springframework.data.domain.Page<com.fooddelivery.ledger.entity.LedgerEntry> entries = ledgerService.getEntries(transactionId, ownerId, ownerType, category, direction, pageable);
        
        com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.ledger.entity.LedgerEntry> pageResponse = com.fooddelivery.common.dto.PageResponseDto.of(entries);
        return ResponseEntity.ok(ApiResponse.success(pageResponse, "Ledger entries retrieved"));
    }

    @PreAuthorize("hasRole(\'ADMIN\')")
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.ledger.dto.LedgerTransactionDto>>> getTransactions(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) UUID transactionId, @RequestParam(required = false) UUID ownerId, @RequestParam(required = false) LedgerAccountType ownerType, @RequestParam(required = false) com.fooddelivery.common.enums.ChargeCategory category, @RequestParam(required = false) com.fooddelivery.common.enums.TransactionDirection direction) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<com.fooddelivery.ledger.dto.LedgerTransactionDto> transactions = ledgerService.getTransactions(transactionId, ownerId, ownerType, category, direction, pageable);
        
        com.fooddelivery.common.dto.PageResponseDto<com.fooddelivery.ledger.dto.LedgerTransactionDto> pageResponse = com.fooddelivery.common.dto.PageResponseDto.of(transactions);
        return ResponseEntity.ok(ApiResponse.success(pageResponse, "Ledger transactions retrieved"));
    }

}
