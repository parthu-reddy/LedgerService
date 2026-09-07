package com.fooddelivery.ledger.reconciliation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest(ReconciliationController.class)
@org.springframework.context.annotation.Import(com.fooddelivery.common.security.CommonSecurityConfig.class)
class ReconciliationControllerTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private ReconciliationService reconciliationService;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.fooddelivery.common.security.IdentityTokenService identityTokenService;
    
    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.data.redis.core.RedisOperations<String, String> redisOperations;

    @org.springframework.boot.test.mock.mockito.MockBean
    private ReconciliationRunRepository reconciliationRunRepository;

    @org.springframework.boot.test.mock.mockito.MockBean
    private ReconciliationBreakRepository reconciliationBreakRepository;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "USER")
    void getBreaks_withUserRole_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/admin/reconciliation/runs/" + java.util.UUID.randomUUID() + "/breaks"))
               .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getBreaks_withAdminRole_isOk() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/admin/reconciliation/runs/" + java.util.UUID.randomUUID() + "/breaks"))
               .andExpect(status().isOk());
    }
}
