package com.fooddelivery.ledger.controller;

import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.dto.ledger.LedgerStatementLineDto;
import com.fooddelivery.ledger.service.StatementQueryService;
import com.fooddelivery.common.security.money.MoneyAccessPolicy;
import com.fooddelivery.common.security.money.MoneyOwnerType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerStatementController {

    private final StatementQueryService statementQueryService;
    private final MoneyAccessPolicy moneyAccessPolicy;

    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESTAURANT', 'DELIVERY', 'ADMIN')")
    @GetMapping("/statements/{ownerType}/{ownerId}")
    public ResponseEntity<Page<LedgerStatementLineDto>> getStatement(
            @PathVariable LedgerAccountType ownerType,
            @PathVariable UUID ownerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) Boolean settled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        MoneyOwnerType moneyOwnerType;
        if (ownerType == LedgerAccountType.CUSTOMER_CREDIT) {
            moneyOwnerType = MoneyOwnerType.CUSTOMER;
        } else if (ownerType == LedgerAccountType.RESTAURANT_PAYABLE) {
            moneyOwnerType = MoneyOwnerType.RESTAURANT;
        } else if (ownerType == LedgerAccountType.DRIVER_PAYABLE) {
            moneyOwnerType = MoneyOwnerType.DRIVER;
        } else if (ownerType == LedgerAccountType.ADVERTISER_PREPAID) {
            moneyOwnerType = MoneyOwnerType.ADVERTISER;
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid owner type");
        }

        if (!moneyAccessPolicy.canAccessMoney(authentication, moneyOwnerType, ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<LedgerStatementLineDto> statement = statementQueryService.getStatement(ownerType, ownerId, from, to, settled, pageable);
        
        return ResponseEntity.ok(statement);
    }

    @PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
    @GetMapping("/statements/references/{referenceId}")
    public ResponseEntity<java.util.List<LedgerStatementLineDto>> getStatementByReference(
            @PathVariable UUID referenceId) {
        return ResponseEntity.ok(statementQueryService.getStatementByReferenceId(referenceId));
    }
}
