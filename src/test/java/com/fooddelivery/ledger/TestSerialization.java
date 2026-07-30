package com.fooddelivery.ledger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

public class TestSerialization {

    @Test
    public void testPageSerialization() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        Page<?> page = Page.empty(PageRequest.of(0, 20));
        System.out.println(mapper.writeValueAsString(page));
    }
}
