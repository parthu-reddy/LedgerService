package com.fooddelivery.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * Deliberately does NOT use the contract-test profile: that profile sets
 * spring.main.web-application-type=none, which removes MockMvc and this is a web test.
 * The datasource properties are inline for the same reason.
 */
@SpringBootTest(properties = {
        "spring.redis.enabled=false", "spring.main.allow-bean-definition-overriding=true",
        "eureka.client.enabled=false",
        "spring.cloud.config.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc(addFilters = false)
public class MockRequestTest {

    /** RateLimitingService needs a concrete LettuceBasedProxyManager, and Redis is not up here. */
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.fooddelivery.common.service.RateLimitingService rateLimitingService;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.fooddelivery.ledger.client.RestaurantBeneficiaryFeignClient restaurantBeneficiaryFeignClient;

    @org.springframework.boot.test.mock.mockito.MockBean
    private com.fooddelivery.ledger.client.DriverBeneficiaryFeignClient driverBeneficiaryFeignClient;

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGetEntriesWithBadUUID() throws Exception {
        mockMvc.perform(get("/api/v1/ledger/admin/entries")
                .param("ownerId", "4ae86b19-25f4- 4123-bd76-5f591eaabd6b")
                .param("ownerType", "CUSTOMER"))
                .andExpect(status().isBadRequest());
    }
}
