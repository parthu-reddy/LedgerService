package com.fooddelivery.ledger.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.ledger.listener.LedgerEventListener;
import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
import com.fooddelivery.ledger.repository.ILedgerRejectionRepository;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.UUID;

/**
 * Producer-side base for LedgerService messaging contracts.
 *
 * Autoconfiguration exclusions are passed as {@code properties} rather than as an
 * {@code @EnableAutoConfiguration(exclude = ...)} attribute on the nested config: every service in
 * this workspace component-scans {@code com.fooddelivery}, so a nested {@code @SpringBootConfiguration}
 * carrying exclusions gets picked up by unrelated tests and breaks them.
 */
@SpringBootTest(
        classes = BaseMessagingClass.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
        })
@org.springframework.test.context.ActiveProfiles("contract-test")
@AutoConfigureMessageVerifier
@EmbeddedKafka(partitions = 1, topics = {"ledger-events"})
public abstract class BaseMessagingClass {

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    @org.springframework.context.annotation.Import(LedgerEventListener.class)
    @org.springframework.context.annotation.Profile("contract-test")
    static class TestConfig {
        @Bean
        public KafkaMessageVerifier kafkaMessageVerifier() {
            return new KafkaMessageVerifier();
        }

        @Bean
        public DoubleEntryLedgerService doubleEntryLedgerService() {
            return Mockito.mock(DoubleEntryLedgerService.class);
        }

        @Bean
        public ILedgerRejectionRepository ledgerRejectionRepository() {
            return Mockito.mock(ILedgerRejectionRepository.class);
        }

        @Bean
        public com.fooddelivery.common.repository.IIdempotencyKeyRepository idempotencyKeyRepository() {
            return Mockito.mock(com.fooddelivery.common.repository.IIdempotencyKeyRepository.class);
        }

        @Bean
        public org.springframework.transaction.support.TransactionTemplate transactionTemplate() {
            return Mockito.mock(org.springframework.transaction.support.TransactionTemplate.class);
        }
    }

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers", "localhost:9092"));
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /** The same auto-configured mapper LedgerEventListener is injected with. */
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LedgerEventListener ledgerEventListener;

    public void fireLedgerDlqEvent() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("transferId", UUID.randomUUID().toString());
        payload.put("amount", "250.00");
        payload.put("chargeCategory", "AD_CLICK");
        payload.put("fromId", UUID.randomUUID().toString());
        payload.put("fromType", "ADVERTISER_WALLET");
        payload.put("toId", UUID.randomUUID().toString());
        payload.put("toType", "PLATFORM");

        ledgerEventListener.handleDltEvent(objectMapper.writeValueAsString(payload), "ledger-events-dlt", Collections.emptyMap());
    }
}
