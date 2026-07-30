package com.fooddelivery.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
public class MockRequestTest {

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
