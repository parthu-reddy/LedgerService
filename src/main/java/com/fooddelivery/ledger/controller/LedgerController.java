package com.fooddelivery.ledger.controller;

import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.common.enums.AccountType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Slf4j
public class LedgerController {

    private final ILedgerAccountRepository accountRepository;
    private final com.fooddelivery.ledger.service.DoubleEntryLedgerService ledgerService;

    // We assume the system account ID for the platform is a fixed UUID
    private static final UUID PLATFORM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @PreAuthorize("hasAnyRole('CUSTOMER', 'RESTAURANT', 'DELIVERY', 'ADMIN')")
    @GetMapping("/accounts/{ownerType}/{ownerId}")
    public ResponseEntity<LedgerAccount> getAccount(
            Principal principal,
            @PathVariable AccountType ownerType, 
            @PathVariable UUID ownerId) {
            
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                
        // Validation: user can only fetch their own ledger account unless they are an ADMIN
        if (!isAdmin && !ownerId.toString().equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: You cannot view this ledger account");
        }

        return accountRepository.findByOwnerIdAndOwnerType(ownerId, ownerType)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/payouts/pending")
    public ResponseEntity<java.util.List<LedgerAccount>> getPendingPayouts() {
        return ResponseEntity.ok(
            accountRepository.findByOwnerTypeInAndBalanceGreaterThan(
                java.util.List.of(AccountType.RESTAURANT, AccountType.DRIVER), 
                java.math.BigDecimal.ZERO
            )
        );
    }
    
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/payouts/settle")
    public ResponseEntity<java.util.Map<String, String>> settlePayout(@RequestBody com.fooddelivery.ledger.dto.PayoutSettlementRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Settlement amount must be positive"));
        }
        
        UUID transferId = UUID.randomUUID();
        // Settle liability: Source = Restaurant/Driver (decreases balance), Target = Platform (increases balance)
        ledgerService.recordTransaction(
            transferId,
            request.getOwnerId(),
            request.getOwnerType(),
            PLATFORM_ACCOUNT_ID,
            AccountType.PLATFORM,
            request.getAmount(),
            com.fooddelivery.common.enums.ChargeCategory.PAYOUT
        );
        
        return ResponseEntity.ok(java.util.Map.of(
            "message", "Settlement recorded successfully",
            "transferId", transferId.toString()
        ));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/entries")
    public ResponseEntity<org.springframework.data.domain.Page<com.fooddelivery.ledger.entity.LedgerEntry>> getEntries(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID transactionId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) AccountType ownerType,
            @RequestParam(required = false) com.fooddelivery.common.enums.ChargeCategory category,
            @RequestParam(required = false) com.fooddelivery.common.enums.TransactionDirection direction
    ) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        org.springframework.data.domain.Page<com.fooddelivery.ledger.entity.LedgerEntry> entries = ledgerService.getEntries(
                transactionId, ownerId, ownerType, category, direction, pageable
        );
        return ResponseEntity.ok(entries);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/transactions")
    public ResponseEntity<org.springframework.data.domain.Page<com.fooddelivery.ledger.dto.LedgerTransactionDto>> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID transactionId,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) AccountType ownerType,
            @RequestParam(required = false) com.fooddelivery.common.enums.ChargeCategory category,
            @RequestParam(required = false) com.fooddelivery.common.enums.TransactionDirection direction
    ) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<com.fooddelivery.ledger.dto.LedgerTransactionDto> transactions = ledgerService.getTransactions(
                transactionId, ownerId, ownerType, category, direction, pageable
        );
        return ResponseEntity.ok(transactions);
    }
}
