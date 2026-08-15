package com.fooddelivery.ledger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.common.enums.AccountType;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.ledger.controller.LedgerController;
import com.fooddelivery.ledger.dto.PayoutSettlementRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import java.security.Principal;
import java.util.UUID;

@Service
@lombok.extern.slf4j.Slf4j
public class LedgerMcpService {
    @java.lang.SuppressWarnings("all")

    private final LedgerController ledgerController;
    private final ObjectMapper objectMapper;

    public LedgerMcpService(LedgerController ledgerController, ObjectMapper objectMapper) {
        this.ledgerController = ledgerController;
        this.objectMapper = objectMapper;
    }

    private Principal createMockAdminPrincipal() {
        // Return a dummy principal name, the mock security context (if any) would provide ROLE_ADMIN
        return () -> "admin-mcp";
    }

    @Tool(description = "Get ledger account. Provide ownerType (e.g. CUSTOMER, RESTAURANT, DRIVER, PLATFORM) and ownerId (UUID).")
    public String getAccount(String ownerType, String ownerId) {
        try {
            return objectMapper.writeValueAsString(ledgerController.getAccount(createMockAdminPrincipal(), AccountType.valueOf(ownerType.toUpperCase()), UUID.fromString(ownerId)).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Get pending payouts for restaurants and drivers.")
    public String getPendingPayouts() {
        try {
            return objectMapper.writeValueAsString(ledgerController.getPendingPayouts().getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Settle a payout. Provide JSON string of PayoutSettlementRequest (ownerId, ownerType, amount).")
    public String settlePayout(String requestJson) {
        try {
            PayoutSettlementRequest req = objectMapper.readValue(requestJson, PayoutSettlementRequest.class);
            return objectMapper.writeValueAsString(ledgerController.settlePayout(req).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Get ledger entries for admin. Provide page, size, transactionId, ownerId, ownerType, category, direction (all optional except page/size).")
    public String getEntries(int page, int size, String transactionId, String ownerId, String ownerType, String category, String direction) {
        try {
            UUID tId = transactionId != null && !transactionId.isEmpty() ? UUID.fromString(transactionId) : null;
            UUID oId = ownerId != null && !ownerId.isEmpty() ? UUID.fromString(ownerId) : null;
            AccountType oType = ownerType != null && !ownerType.isEmpty() ? AccountType.valueOf(ownerType.toUpperCase()) : null;
            ChargeCategory cat = category != null && !category.isEmpty() ? ChargeCategory.valueOf(category.toUpperCase()) : null;
            TransactionDirection dir = direction != null && !direction.isEmpty() ? TransactionDirection.valueOf(direction.toUpperCase()) : null;
            return objectMapper.writeValueAsString(ledgerController.getEntries(page, size, tId, oId, oType, cat, dir).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Get ledger transactions for admin. Provide page, size, transactionId, ownerId, ownerType, category, direction (all optional except page/size).")
    public String getTransactions(int page, int size, String transactionId, String ownerId, String ownerType, String category, String direction) {
        try {
            UUID tId = transactionId != null && !transactionId.isEmpty() ? UUID.fromString(transactionId) : null;
            UUID oId = ownerId != null && !ownerId.isEmpty() ? UUID.fromString(ownerId) : null;
            AccountType oType = ownerType != null && !ownerType.isEmpty() ? AccountType.valueOf(ownerType.toUpperCase()) : null;
            ChargeCategory cat = category != null && !category.isEmpty() ? ChargeCategory.valueOf(category.toUpperCase()) : null;
            TransactionDirection dir = direction != null && !direction.isEmpty() ? TransactionDirection.valueOf(direction.toUpperCase()) : null;
            return objectMapper.writeValueAsString(ledgerController.getTransactions(page, size, tId, oId, oType, cat, dir).getBody());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
