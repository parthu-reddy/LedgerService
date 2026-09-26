package com.fooddelivery.ledger.controller;

import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.common.time.TimeWindow;
import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
import com.fooddelivery.ledger.service.StatementQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/v1/internal/ledger/accounts/{ownerType}/{ownerId}/totals, with the platform's exception advice mounted. */
class InternalLedgerCategoryTotalControllerTest {

    private static final UUID OUTLET = UUID.fromString("0c9a8b7d-1e2f-4a3b-8c4d-5e6f7a8b9c01");
    private static final String PATH = "/api/v1/internal/ledger/accounts/RESTAURANT_PAYABLE/" + OUTLET + "/totals";

    private final DoubleEntryLedgerService ledgerService = Mockito.mock(DoubleEntryLedgerService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InternalLedgerController(ledgerService, Mockito.mock(StatementQueryService.class)))
                .setControllerAdvice(new com.fooddelivery.common.exception.GlobalExceptionHandler()).build();
    }

    /** The outlet's clawbacks over a London week across the fall-back, exactly as asked. */
    @Test
    void returnsTheTotalForTheOwnerCategoryDirectionAndWindowAsked() throws Exception {
        TimeWindow week = new TimeWindow(Instant.parse("2026-10-18T23:00:00Z"), Instant.parse("2026-10-26T00:00:00Z"));
        when(ledgerService.getCategoryTotal(LedgerAccountType.RESTAURANT_PAYABLE, OUTLET, ChargeCategory.CLAWBACK,
                TransactionDirection.DEBIT, week)).thenReturn(new BigDecimal("42.50"));

        mockMvc.perform(get(PATH).param("category", "CLAWBACK").param("direction", "DEBIT")
                        .param("from", "2026-10-18T23:00:00Z").param("to", "2026-10-26T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(content().string("42.50"));

        verify(ledgerService).getCategoryTotal(LedgerAccountType.RESTAURANT_PAYABLE, OUTLET, ChargeCategory.CLAWBACK,
                TransactionDirection.DEBIT, week);
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NONE", value = {
            "CLAWBACK, DEBIT, NONE, 2026-10-26T00:00:00Z",
            "CLAWBACK, DEBIT, 2026-10-18T23:00:00, 2026-10-26T00:00:00Z",
            "CLAWBACK, DEBIT, 2026-10-26T00:00:00Z, 2026-10-18T23:00:00Z",
            "CLAWBACK, DEBIT, 2026-10-26T00:00:00Z, 2026-10-26T00:00:00Z",
            "CLAWBACKS, DEBIT, 2026-10-18T23:00:00Z, 2026-10-26T00:00:00Z",
            "CLAWBACK, OUT, 2026-10-18T23:00:00Z, 2026-10-26T00:00:00Z",
            "NONE, DEBIT, 2026-10-18T23:00:00Z, 2026-10-26T00:00:00Z"})
    void aMissingOrUnreadableParameterOrAnInvertedWindowIsA400(String category, String direction, String from, String to) throws Exception {
        var request = get(PATH);
        if (category != null) request.param("category", category);
        if (direction != null) request.param("direction", direction);
        if (from != null) request.param("from", from);
        if (to != null) request.param("to", to);

        mockMvc.perform(request).andExpect(status().isBadRequest());

        verifyNoInteractions(ledgerService);
    }

    @Test
    void anUnknownAccountTypeIsA400() throws Exception {
        mockMvc.perform(get("/api/v1/internal/ledger/accounts/RESTAURANT/" + OUTLET + "/totals")
                        .param("category", "CLAWBACK").param("direction", "DEBIT")
                        .param("from", "2026-10-18T23:00:00Z").param("to", "2026-10-26T00:00:00Z"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(ledgerService);
    }
}
