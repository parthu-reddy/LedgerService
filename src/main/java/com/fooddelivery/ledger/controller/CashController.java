package com.fooddelivery.ledger.controller;

import com.fooddelivery.ledger.dto.CashRemittanceRequest;
import com.fooddelivery.ledger.entity.CashRemittance;
import com.fooddelivery.ledger.service.CashService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/admin/cash")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CashController {

    private final CashService cashService;
    private UUID getAdminId() {
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return UUID.fromString(auth.getName());
    }

    @PostMapping("/remit")
    public ResponseEntity<CashRemittance> remitCash(@RequestBody CashRemittanceRequest request) {
        CashRemittance remittance = cashService.recordCashRemittance(request, getAdminId());
        return ResponseEntity.ok(remittance);
    }

    @GetMapping("/drivers/{driverId}/summary")
    public ResponseEntity<com.fooddelivery.common.dto.ledger.CashSummaryDto> getCashSummary(@PathVariable UUID driverId) {
        return ResponseEntity.ok(cashService.getCashSummary(driverId));
    }

    @GetMapping("/drivers/{driverId}")
    public ResponseEntity<org.springframework.data.domain.Page<CashRemittance>> getCashByDriver(
            @PathVariable UUID driverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(cashService.getCashRemittancesByDriver(driverId, pageable));
    }
}
