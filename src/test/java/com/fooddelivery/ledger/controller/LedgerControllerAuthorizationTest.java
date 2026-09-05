package com.fooddelivery.ledger.controller;

import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.security.money.MoneyAccessPolicy;
import com.fooddelivery.common.security.money.MoneyOwnerType;
import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import org.springframework.context.annotation.Import;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@WebMvcTest(controllers = {LedgerController.class, PayoutController.class})
@Import({LedgerControllerAuthorizationTest.ObservationConfig.class, com.fooddelivery.common.security.CommonSecurityConfig.class, LedgerController.class, PayoutController.class})
public class LedgerControllerAuthorizationTest {

    @Configuration
    static class ObservationConfig {
        @Bean
        public ObservationRegistry observationRegistry() {
            return ObservationRegistry.create();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ILedgerAccountRepository accountRepository;

    @MockBean
    private DoubleEntryLedgerService ledgerService;

    @MockBean
    private com.fooddelivery.ledger.service.PayoutService payoutService;

    @MockBean
    private MoneyAccessPolicy moneyAccessPolicy;

    @MockBean
    private com.fooddelivery.common.security.IdentityTokenService identityTokenService;

    @MockBean
    private org.springframework.data.redis.core.RedisOperations<String, String> redisOperations;

    private final UUID ownerId = UUID.randomUUID();

    @Autowired
    private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping requestMappingHandlerMapping;

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @BeforeEach
    void setUp() {
        LedgerAccount account = new LedgerAccount();
        account.setId(UUID.randomUUID());
        when(accountRepository.findByOwnerIdAndOwnerType(any(UUID.class), any(LedgerAccountType.class)))
                .thenReturn(Optional.of(account));
    }

    // --- CUSTOMER tests ---

    @Test
    @WithMockUser(username = "test-user", roles = {"CUSTOMER"})
    void getAccount_Customer_Allowed_WhenPolicyAllows() throws Exception {
        when(moneyAccessPolicy.canAccessMoney(any(), eq(MoneyOwnerType.CUSTOMER), eq(ownerId)))
                .thenReturn(true);

        mockMvc.perform(get("/api/v1/ledger/accounts/CUSTOMER_CREDIT/" + ownerId))
                .andDo(print())
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "test-user", roles = {"CUSTOMER"})
    void getAccount_Customer_Forbidden_WhenPolicyDenies() throws Exception {
        when(moneyAccessPolicy.canAccessMoney(any(), eq(MoneyOwnerType.CUSTOMER), eq(ownerId)))
                .thenReturn(false);

        mockMvc.perform(get("/api/v1/ledger/accounts/CUSTOMER_CREDIT/" + ownerId))
                .andExpect(status().isForbidden());
    }

    // --- RESTAURANT tests ---

    @Test
    @WithMockUser(username = "restaurant-owner", roles = {"RESTAURANT"})
    void getAccount_Restaurant_Allowed_WhenOwnerOfOutlet() throws Exception {
        when(moneyAccessPolicy.canAccessMoney(any(), eq(MoneyOwnerType.RESTAURANT), eq(ownerId)))
                .thenReturn(true);

        mockMvc.perform(get("/api/v1/ledger/accounts/RESTAURANT_PAYABLE/" + ownerId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "other-restaurant-user", roles = {"RESTAURANT"})
    void getAccount_Restaurant_Forbidden_WhenNotOwnerOfOutlet() throws Exception {
        when(moneyAccessPolicy.canAccessMoney(any(), eq(MoneyOwnerType.RESTAURANT), eq(ownerId)))
                .thenReturn(false);

        mockMvc.perform(get("/api/v1/ledger/accounts/RESTAURANT_PAYABLE/" + ownerId))
                .andExpect(status().isForbidden());
    }

    // --- DRIVER tests ---

    @Test
    @WithMockUser(username = "driver-user", roles = {"DELIVERY"})
    void getAccount_Driver_Allowed_WhenOwnAccount() throws Exception {
        when(moneyAccessPolicy.canAccessMoney(any(), eq(MoneyOwnerType.DRIVER), eq(ownerId)))
                .thenReturn(true);

        mockMvc.perform(get("/api/v1/ledger/accounts/DRIVER_PAYABLE/" + ownerId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "other-driver", roles = {"DELIVERY"})
    void getAccount_Driver_Forbidden_WhenOtherDriverAccount() throws Exception {
        when(moneyAccessPolicy.canAccessMoney(any(), eq(MoneyOwnerType.DRIVER), eq(ownerId)))
                .thenReturn(false);

        mockMvc.perform(get("/api/v1/ledger/accounts/DRIVER_PAYABLE/" + ownerId))
                .andExpect(status().isForbidden());
    }

    // --- ADMIN tests ---

    @Test
    @WithMockUser(username = "admin-user", roles = {"ADMIN"})
    void getAccount_Admin_AlwaysAllowed() throws Exception {
        when(moneyAccessPolicy.canAccessMoney(any(), eq(MoneyOwnerType.CUSTOMER), eq(ownerId)))
                .thenReturn(true);

        mockMvc.perform(get("/api/v1/ledger/accounts/CUSTOMER_CREDIT/" + ownerId))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin-user", roles = {"ADMIN"})
    void getPendingPayouts_Admin_Allowed() throws Exception {
        when(accountRepository.findByOwnerTypeInAndBalanceGreaterThan(any(), any()))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/v1/admin/payouts/pending"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "driver-user", roles = {"DELIVERY"})
    void getPendingPayouts_Driver_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/admin/payouts/pending"))
                .andExpect(status().isForbidden());
    }
}
