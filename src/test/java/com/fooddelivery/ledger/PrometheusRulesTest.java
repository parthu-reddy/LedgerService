package com.fooddelivery.ledger;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PrometheusRulesTest {

    @Test
    void testRulesExist() {
        Path deploymentPath = Paths.get("../Deployment");
        assumeTrue(Files.exists(deploymentPath), "Deployment directory not found (likely running in isolated CI); skipping test.");

        Path rulesPath = Paths.get("../Deployment/prometheus/rules/money.yml");
        assertTrue(Files.exists(rulesPath), "money.yml should exist");
    }
}
