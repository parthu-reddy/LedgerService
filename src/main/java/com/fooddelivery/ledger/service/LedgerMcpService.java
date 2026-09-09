package com.fooddelivery.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.ledger.controller.LedgerController;
import com.fooddelivery.ledger.service.PayoutService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
public class LedgerMcpService {

    private final LedgerController ledgerController;
    private final com.fooddelivery.ledger.controller.AdminLedgerController adminLedgerController;
    private final com.fooddelivery.ledger.controller.InternalLedgerController internalLedgerController;
    private final ObjectMapper objectMapper;
    private final PayoutService payoutService;

    public LedgerMcpService(LedgerController ledgerController, com.fooddelivery.ledger.controller.AdminLedgerController adminLedgerController, com.fooddelivery.ledger.controller.InternalLedgerController internalLedgerController, ObjectMapper objectMapper, PayoutService payoutService) {
        this.ledgerController = ledgerController;
        this.adminLedgerController = adminLedgerController;
        this.internalLedgerController = internalLedgerController;
        this.objectMapper = objectMapper;
        this.payoutService = payoutService;
    }

    /**
     * Verifies the current SecurityContext holds ROLE_ADMIN.
     * MCP tools must not bypass the security model with mock principals.
     */
    private void requireAdminRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new org.springframework.security.access.AccessDeniedException("MCP tool requires an authenticated principal");
        }
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException("MCP tool requires ROLE_ADMIN");
        }
    }

    private Principal getCurrentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth : () -> "anonymous";
    }

    @Tool(description = "Get ledger account. Provide ownerType (e.g. CUSTOMER, RESTAURANT, DRIVER, PLATFORM) and ownerId (UUID).")
    public String getAccount(String ownerType, String ownerId) {
        try {
            // getAccount uses MoneyAccessPolicy internally, so it self-authorises based on the real principal
            return objectMapper.writeValueAsString(ledgerController.getAccount(getCurrentPrincipal(), LedgerAccountType.valueOf(ownerType.toUpperCase()), UUID.fromString(ownerId)).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Get pending payouts for restaurants and drivers.")
    public String getPendingPayouts() {
        try {
            requireAdminRole();
            return objectMapper.writeValueAsString(payoutService.pending());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Get ledger entries for admin. Provide page, size, transactionId, ownerId, ownerType, category, direction (all optional except page/size).")
    public String getEntries(int page, int size, String transactionId, String ownerId, String ownerType, String category, String direction) {
        try {
            requireAdminRole();
            UUID tId = transactionId != null && !transactionId.isEmpty() ? UUID.fromString(transactionId) : null;
            UUID oId = ownerId != null && !ownerId.isEmpty() ? UUID.fromString(ownerId) : null;
            LedgerAccountType oType = ownerType != null && !ownerType.isEmpty() ? LedgerAccountType.valueOf(ownerType.toUpperCase()) : null;
            ChargeCategory cat = category != null && !category.isEmpty() ? ChargeCategory.valueOf(category.toUpperCase()) : null;
            TransactionDirection dir = direction != null && !direction.isEmpty() ? TransactionDirection.valueOf(direction.toUpperCase()) : null;
            return objectMapper.writeValueAsString(adminLedgerController.getEntries(page, size, tId, oId, oType, cat, dir).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Get ledger transactions for admin. Provide page, size, transactionId, ownerId, ownerType, category, direction (all optional except page/size).")
    public String getTransactions(int page, int size, String transactionId, String ownerId, String ownerType, String category, String direction) {
        try {
            requireAdminRole();
            UUID tId = transactionId != null && !transactionId.isEmpty() ? UUID.fromString(transactionId) : null;
            UUID oId = ownerId != null && !ownerId.isEmpty() ? UUID.fromString(ownerId) : null;
            LedgerAccountType oType = ownerType != null && !ownerType.isEmpty() ? LedgerAccountType.valueOf(ownerType.toUpperCase()) : null;
            ChargeCategory cat = category != null && !category.isEmpty() ? ChargeCategory.valueOf(category.toUpperCase()) : null;
            TransactionDirection dir = direction != null && !direction.isEmpty() ? TransactionDirection.valueOf(direction.toUpperCase()) : null;
            return objectMapper.writeValueAsString(adminLedgerController.getTransactions(page, size, tId, oId, oType, cat, dir).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
