package com.fooddelivery.ledger.controller;

import com.fooddelivery.common.dto.ledger.LedgerStatementLineDto;
import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
import com.fooddelivery.ledger.service.StatementQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/ledger")
@PreAuthorize("hasAnyRole('SERVICE', 'ADMIN')")
public class InternalLedgerController {

    private final DoubleEntryLedgerService ledgerService;
    private final StatementQueryService statementQueryService;

    public InternalLedgerController(DoubleEntryLedgerService ledgerService, StatementQueryService statementQueryService) {
        this.ledgerService = ledgerService;
        this.statementQueryService = statementQueryService;
    }

    /** Read by ONDC settlement reconciliation, which runs as background work. */
    @GetMapping("/orders/{orderId}/total")
    public ResponseEntity<BigDecimal> getOrderLedgerAmount(@PathVariable String orderId) {
        final UUID referenceId;
        try {
            referenceId = UUID.fromString(orderId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "orderId must be the order's reference UUID; received: " + orderId);
        }
        return ResponseEntity.ok(ledgerService.getOrderLedgerTotal(referenceId));
    }

    @GetMapping("/statements/references/{referenceId}")
    public ResponseEntity<List<LedgerStatementLineDto>> getStatementByReference(
            @PathVariable UUID referenceId) {
        return ResponseEntity.ok(statementQueryService.getStatementByReferenceId(referenceId));
    }
}
