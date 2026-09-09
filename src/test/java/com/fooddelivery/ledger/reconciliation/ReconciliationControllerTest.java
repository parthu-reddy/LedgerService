package com.fooddelivery.ledger.reconciliation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        mockMvc.perform(get("/api/v1/internal/admin/ledger/reconciliation/runs/" + java.util.UUID.randomUUID() + "/breaks"))
               .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void getBreaks_withAdminRole_isOk() throws Exception {
        mockMvc.perform(get("/api/v1/internal/admin/ledger/reconciliation/runs/" + java.util.UUID.randomUUID() + "/breaks"))
               .andExpect(status().isOk());
    }

    // ----------------------------------------------------------------- every mapping, not one

    /**
     * The whole surface, because only {@code getBreaks} was covered.
     *
     * <p>Deleting {@code @PreAuthorize("hasRole('ADMIN')")} from {@code resolveBreak} -- the call
     * that closes a money break and takes it off the operator's queue -- left both existing tests
     * green. Found 2026-09-09 performing Phase 7's break-test 3.
     */
    static Stream<Arguments> everyMapping() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        String base = "/api/v1/internal/admin/ledger/reconciliation";
        return Stream.of(
                Arguments.of("GET runs", get(base + "/runs")),
                Arguments.of("GET runs/{id}", get(base + "/runs/" + id)),
                Arguments.of("GET runs/{id}/breaks", get(base + "/runs/" + id + "/breaks")),
                Arguments.of("POST runs/{id}/execute", post(base + "/runs/" + id + "/execute")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())),
                Arguments.of("POST runs/{id}/resolve-breaks", post(base + "/runs/" + id + "/resolve-breaks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolvedBy\":\"22222222-2222-2222-2222-222222222222\",\"note\":\"handled\"}")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())));
    }

    @ParameterizedTest(name = "{0} is refused for a non-admin")
    @MethodSource("everyMapping")
    @WithMockUser(roles = "USER")
    void everyMappingIsAdminOnly(String name, MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0} is reachable by an admin")
    @MethodSource("everyMapping")
    @WithMockUser(roles = "ADMIN")
    void everyMappingIsReachableByAnAdmin(String name, MockHttpServletRequestBuilder request) throws Exception {
        // Not asserting 200: resolve-breaks on an id with no row is a legitimate 4xx/5xx from the
        // handler. What matters is that the request was not turned away at the security layer.
        mockMvc.perform(request).andExpect(result ->
                org.junit.jupiter.api.Assertions.assertNotEquals(403, result.getResponse().getStatus(),
                        name + " was refused for an admin"));
    }
}
