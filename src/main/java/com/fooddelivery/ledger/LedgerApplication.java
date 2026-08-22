package com.fooddelivery.ledger;

import com.fooddelivery.common.outbox.config.EnableOutbox;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
    scanBasePackages = {"com.fooddelivery.ledger", "com.fooddelivery.common"}
)
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = {"com.fooddelivery.ledger", "com.fooddelivery.common.repository"})
@org.springframework.boot.autoconfigure.domain.EntityScan(basePackages = {"com.fooddelivery.ledger", "com.fooddelivery.common.entity"})
@EnableDiscoveryClient
@EnableKafka
@EnableOutbox
@EnableScheduling
@EnableAsync
public class LedgerApplication {
    public static void main(String[] args) {
        SpringApplication.run(LedgerApplication.class, args);
    }
}
