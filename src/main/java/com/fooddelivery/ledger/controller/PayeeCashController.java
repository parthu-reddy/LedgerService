package com.fooddelivery.ledger.controller;

import com.fooddelivery.common.dto.PageResponseDto;
import com.fooddelivery.common.dto.ledger.CashRemittanceDto;
import com.fooddelivery.common.security.money.MoneyAccessPolicy;
import com.fooddelivery.common.security.money.MoneyOwnerType;
import com.fooddelivery.ledger.entity.CashRemittance;
import com.fooddelivery.ledger.service.CashService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ledger/cash")
@RequiredArgsConstructor
public class PayeeCashController {

    private final CashService cashService;
    private final MoneyAccessPolicy moneyAccessPolicy;

    @PreAuthorize("hasAnyRole('DELIVERY', 'ADMIN')")
    @GetMapping("/drivers/{driverId}/summary")
    public ResponseEntity<com.fooddelivery.common.dto.ledger.CashSummaryDto> getCashSummary(@PathVariable UUID driverId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!moneyAccessPolicy.canAccessMoney(authentication, MoneyOwnerType.DRIVER, driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }
        return ResponseEntity.ok(cashService.getCashSummary(driverId));
    }

    @PreAuthorize("hasAnyRole('DELIVERY', 'ADMIN')")
    @GetMapping("/drivers/{driverId}")
    public ResponseEntity<PageResponseDto<CashRemittanceDto>> getCashByDriver(
            @PathVariable UUID driverId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
            
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (!moneyAccessPolicy.canAccessMoney(authentication, MoneyOwnerType.DRIVER, driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<CashRemittance> remittances = cashService.getCashRemittancesByDriver(driverId, pageable);
        
        PageResponseDto<CashRemittanceDto> response = PageResponseDto.<CashRemittanceDto>builder()
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
                .build();
        return ResponseEntity.ok(response);
    }
}
