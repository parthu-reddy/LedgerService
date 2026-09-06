package com.fooddelivery.ledger;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusRulesTest {

    @Test
    void testRulesExist() {
        Path rulesPath = Paths.get("../Deployment/prometheus/rules/money.yml");
        assertTrue(Files.exists(rulesPath), "money.yml should exist");
    }
}
