package com.fooddelivery.ledger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = {"com.fooddelivery.ledger", "com.fooddelivery.common"})
@EntityScan(basePackages = {"com.fooddelivery.ledger", "com.fooddelivery.common"})
public class LedgerJpaConfig {
}
