package com.fooddelivery.ledger.controller;

import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.dto.ledger.LedgerLeg;
import com.fooddelivery.common.dto.ledger.LedgerTransactionCommand;
import com.fooddelivery.common.util.DeterministicIdUtils;
import com.fooddelivery.ledger.dto.CashRemittanceRequest;
import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/internal/ledgers")
public class InternalLedgerController {

    private final DoubleEntryLedgerService ledgerService;
    private static final UUID PLATFORM_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public InternalLedgerController(DoubleEntryLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

}
