package com.fooddelivery.ledger.controller;

import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.common.enums.AccountType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ledger")
@lombok.extern.slf4j.Slf4j
public class LedgerController {
    @java.lang.SuppressWarnings("all")

    private final ILedgerAccountRepository accountRepository;
    private final com.fooddelivery.ledger.service.DoubleEntryLedgerService ledgerService;
    // We assume the system account ID for the platform is a fixed UUID
    private static final UUID PLATFORM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @PreAuthorize("hasAnyRole(\'CUSTOMER\', \'RESTAURANT\', \'DELIVERY\', \'ADMIN\')")
    @GetMapping("/accounts/{ownerType}/{ownerId}")
    public ResponseEntity<LedgerAccount> getAccount(Principal principal, @PathVariable AccountType ownerType, @PathVariable UUID ownerId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        // Validation: user can only fetch their own ledger account unless they are an ADMIN
        if (!isAdmin && !ownerId.toString().equals(principal.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: You cannot view this ledger account");
        }
        return accountRepository.findByOwnerIdAndOwnerType(ownerId, ownerType).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole(\'ADMIN\')")
    @GetMapping("/payouts/pending")
    public ResponseEntity<java.util.List<LedgerAccount>> getPendingPayouts() {
        return ResponseEntity.ok(accountRepository.findByOwnerTypeInAndBalanceGreaterThan(java.util.List.of(AccountType.RESTAURANT, AccountType.DRIVER), java.math.BigDecimal.ZERO));
    }

    @PreAuthorize("hasRole(\'ADMIN\')")
    @PostMapping("/payouts/settle")
    public ResponseEntity<java.util.Map<String, String>> settlePayout(@RequestBody com.fooddelivery.ledger.dto.PayoutSettlementRequest request) {
        if (request.getAmount() == null || request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Settlement amount must be positive"));
        }
        UUID transferId = UUID.randomUUID();
        // Settle liability: Source = Restaurant/Driver (decreases balance), Target = Platform (increases balance)
        ledgerService.recordTransaction(transferId, request.getOwnerId(), request.getOwnerType(), PLATFORM_ACCOUNT_ID, AccountType.PLATFORM, request.getAmount(), com.fooddelivery.common.enums.ChargeCategory.PAYOUT);
        return ResponseEntity.ok(java.util.Map.of("message", "Settlement recorded successfully", "transferId", transferId.toString()));
    }

    /**
     * The ledger's view of one order's total, consumed by ONDCIntegrationService via
     * LedgerServiceClient.getOrderLedgerAmount and compared against what an ONDC counterparty
     * reports during settlement reconciliation. The definition lives in
     * DoubleEntryLedgerService.getOrderLedgerTotal.
     *
     * The path variable is declared as String because the Feign client declares it as String, but
     * it must be the order's reference UUID: LedgerEntry.referenceId is a UUID and there is no
     * business-key lookup. A non-UUID gets 400 rather than a misleading 0.00 -- returning a total
     * for an id we could not parse would be worse than refusing.
     *
     * No @PreAuthorize, matching /payouts/pending: these are service-to-service calls and the Feign
     * client sends no token. Flagged for review rather than secured here, since adding auth would
     * break the caller.
     */
    /** Read by ONDC settlement reconciliation, which runs as background work. */
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
    @GetMapping("/orders/{orderId}/total")
    public ResponseEntity<java.math.BigDecimal> getOrderLedgerAmount(@PathVariable String orderId) {
        final UUID referenceId;
        try {
            referenceId = UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "orderId must be the order's reference UUID; received: " + orderId);
        }
        return ResponseEntity.ok(ledgerService.getOrderLedgerTotal(referenceId));
    }

    @PreAuthorize("hasRole(\'ADMIN\')")
    @GetMapping("/admin/entries")
    public ResponseEntity<org.springframework.data.domain.Page<com.fooddelivery.ledger.entity.LedgerEntry>> getEntries(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) UUID transactionId, @RequestParam(required = false) UUID ownerId, @RequestParam(required = false) AccountType ownerType, @RequestParam(required = false) com.fooddelivery.common.enums.ChargeCategory category, @RequestParam(required = false) com.fooddelivery.common.enums.TransactionDirection direction) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        org.springframework.data.domain.Page<com.fooddelivery.ledger.entity.LedgerEntry> entries = ledgerService.getEntries(transactionId, ownerId, ownerType, category, direction, pageable);
        return ResponseEntity.ok(entries);
    }

    @PreAuthorize("hasRole(\'ADMIN\')")
    @GetMapping("/admin/transactions")
    public ResponseEntity<org.springframework.data.domain.Page<com.fooddelivery.ledger.dto.LedgerTransactionDto>> getTransactions(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) UUID transactionId, @RequestParam(required = false) UUID ownerId, @RequestParam(required = false) AccountType ownerType, @RequestParam(required = false) com.fooddelivery.common.enums.ChargeCategory category, @RequestParam(required = false) com.fooddelivery.common.enums.TransactionDirection direction) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        org.springframework.data.domain.Page<com.fooddelivery.ledger.dto.LedgerTransactionDto> transactions = ledgerService.getTransactions(transactionId, ownerId, ownerType, category, direction, pageable);
        return ResponseEntity.ok(transactions);
    }

    @java.lang.SuppressWarnings("all")
    public LedgerController(final ILedgerAccountRepository accountRepository, final com.fooddelivery.ledger.service.DoubleEntryLedgerService ledgerService) {
        this.accountRepository = accountRepository;
        this.ledgerService = ledgerService;
    }
}
