package com.fooddelivery.ledger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Background reconciliation must not start while a test context is only proving
 * application wiring. The job itself is covered directly by its unit tests.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test & !contract-test")
@EnableScheduling
public class LedgerSchedulingConfiguration {
}
