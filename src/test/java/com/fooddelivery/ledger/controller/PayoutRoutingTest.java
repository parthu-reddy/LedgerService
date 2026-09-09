package com.fooddelivery.ledger.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class PayoutRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessAdminPayouts() throws Exception {
        mockMvc.perform(get("/api/v1/internal/admin/payouts/pending"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void nonAdminCannotAccessAdminPayouts() throws Exception {
        mockMvc.perform(get("/api/v1/internal/admin/payouts/pending"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SERVICE")
    void serviceCanAccessInternalPayouts() throws Exception {
        mockMvc.perform(get("/api/v1/internal/ledger/payouts/latest/RESTAURANT/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAccessInternalPayouts() throws Exception {
        mockMvc.perform(get("/api/v1/internal/ledger/payouts/latest/RESTAURANT/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isOk());
    }
}
