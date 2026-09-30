package com.fooddelivery.ledger.controller;

import com.fooddelivery.ledger.service.PayoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The browser already supplies an operation key for payout actions. These route tests ensure the
 * controller does not accidentally drop that key before the service can bind it to its immutable
 * payout operation record.
 */
@ExtendWith(MockitoExtension.class)
class PayoutActionControllerTest {

    @Mock
    private PayoutService payoutService;

    private MockMvc mockMvc;
    private UUID payoutId;
    private UUID adminId;

    @BeforeEach
    void setUp() {
        payoutId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminId.toString(), "N/A"));
        mockMvc = MockMvcBuilders.standaloneSetup(new PayoutController(payoutService)).build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void actionRoutesPassTheRequiredOperationKeyToTheService() throws Exception {
        mockMvc.perform(post(basePath("approve"))
                        .header("Idempotency-Key", "approve-key"))
                .andExpect(status().isOk());
        verify(payoutService).approve(payoutId, adminId, "approve-key");

        mockMvc.perform(post(basePath("mark-paid"))
                        .param("bankReference", "UTR-100")
                        .header("Idempotency-Key", "paid-key"))
                .andExpect(status().isOk());
        verify(payoutService).markPaid(payoutId, "UTR-100", adminId, "paid-key");

        mockMvc.perform(post(basePath("fail"))
                        .param("reason", "bank rejected the account")
                        .header("Idempotency-Key", "fail-key"))
                .andExpect(status().isOk());
        verify(payoutService).fail(payoutId, "bank rejected the account", adminId, "fail-key");

        mockMvc.perform(post(basePath("cancel"))
                        .header("Idempotency-Key", "cancel-key"))
                .andExpect(status().isOk());
        verify(payoutService).cancel(payoutId, adminId, "cancel-key");
    }

    @Test
    void aPayoutActionWithoutAnOperationKeyIsRejectedBeforeTheServiceRuns() throws Exception {
        mockMvc.perform(post(basePath("approve")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(payoutService);
    }

    @Test
    void requiredSettlementParametersAreRejectedBeforeTheServiceRuns() throws Exception {
        mockMvc.perform(post(basePath("mark-paid"))
                        .header("Idempotency-Key", "paid-key"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(basePath("fail"))
                        .header("Idempotency-Key", "fail-key"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(payoutService);
    }

    private String basePath(String action) {
        return "/api/v1/internal/admin/payouts/" + payoutId + "/" + action;
    }
}
