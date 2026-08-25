package com.fooddelivery.ledger.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fooddelivery.common.constants.KafkaConstants;
import com.fooddelivery.common.enums.AccountType;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.ledger.listener.LedgerEventListener;
import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
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
@EmbeddedKafka(partitions = 1, topics = {"ledger-events-dlq"})
public abstract class BaseMessagingClass {

    @org.springframework.boot.test.context.TestConfiguration
    
    static class TestConfig {
        @Bean
        public KafkaMessageVerifier kafkaMessageVerifier() {
            return new KafkaMessageVerifier();
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

    /**
     * Publishes a ledger failure to ledger-events-dlq through the real production path: the actual
     * {@code @DltHandler} on {@link LedgerEventListener}, holding the real auto-configured
     * KafkaTemplate. Only the collaborators {@code handleDltEvent} never touches are mocked.
     *
     * This is the single compensation route for ledger-rejected debits, so what it pins matters:
     *
     *   1. The DLQ topic is {@code ledger-events-dlq} and comes from the constant, not a literal.
     *   2. The failed payload is forwarded **verbatim** -- flat, at the root, exactly as it arrived
     *      on ledger-events. Wrapping it in an {eventType, payload} envelope, or re-serializing it,
     *      would break WalletService.LedgerFailureConsumer silently. That consumer reads fromType,
     *      fromId, transferId and amount at the root and simply does nothing if they are absent:
     *      no exception, no DLQ, nothing at ERROR.
     *
     * The body mirrors what WalletService.publishLedgerEvent emits for an advertiser debit
     * (isDebit = true -> the advertiser is fromId/fromType, the platform is toId/toType), because
     * fromType == ADVERTISER_WALLET is the only case LedgerFailureConsumer acts on.
     *
     * Note amount is a STRING here, not a JSON number: publishLedgerEvent writes
     * amount.toPlainString().
     */
    public void fireLedgerDlqEvent() throws Exception {
        String transferId = "0f1a5cb3-2b6d-5a1e-9c47-8e3f6d2a1b04";
        UUID advertiserId = UUID.fromString("6b1d3c22-9f45-4a7e-8c11-2d4e6f8a9b02");
        UUID platformId = new UUID(0, 0);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("transferId", transferId);
        payload.put("amount", new BigDecimal("250.00").toPlainString());
        payload.put("chargeCategory", ChargeCategory.AD_CLICK.name());
        payload.put("fromId", advertiserId.toString());
        payload.put("fromType", AccountType.ADVERTISER_WALLET.name());
        payload.put("toId", platformId.toString());
        payload.put("toType", AccountType.PLATFORM.name());

        LedgerEventListener listener = new LedgerEventListener(
                Mockito.mock(DoubleEntryLedgerService.class),
                objectMapper,
                kafkaTemplate,
                Mockito.mock(IIdempotencyKeyRepository.class),
                Mockito.mock(TransactionTemplate.class));

        listener.handleDltEvent(payload.toString(),
                KafkaConstants.TOPIC_LEDGER_EVENTS,
                Collections.emptyMap());
    }
}
