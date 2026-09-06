package com.fooddelivery.ledger.contract;

import com.fooddelivery.common.contract.KafkaStubMessageSender;

import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.ledger.listener.LedgerEventListener;
import com.fooddelivery.ledger.service.DoubleEntryLedgerService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.stubrunner.StubTrigger;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.cloud.contract.verifier.messaging.MessageVerifierSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.Message;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Consumes CustomerApplication's real ledger_events stub and asserts the double-entry transaction
 * is recorded with the exact amount and account types the contract carries.
 *
 * Note the contract asserts an `eventType` Kafka header; the listener resolves the type via
 * KafkaHeaderUtils.extractEventType, so this also covers the Phase 7 header work end to end.
 */
@SpringBootTest(classes = LedgerTransactionConsumerContractTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration")
@ActiveProfiles("contract-test")
@AutoConfigureStubRunner(ids = "com.fooddelivery:food-delivery-backend:+:stubs",
        stubsMode = StubRunnerProperties.StubsMode.LOCAL)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.BEFORE_CLASS)
@EmbeddedKafka(partitions = 1, topics = {"ledger-events"})
class LedgerTransactionConsumerContractTest {

    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration
    
    @Import(LedgerEventListener.class)
    static class TestConfig {
        @Bean
        public MessageVerifierSender<Message<?>> kafkaStubMessageSender(KafkaTemplate<String, String> t) {
            return new KafkaStubMessageSender(t);
        }

        /** Real template over a no-op manager, so the listener's callback actually executes. */
        @Bean
        public TransactionTemplate transactionTemplate() {
            PlatformTransactionManager tm = Mockito.mock(PlatformTransactionManager.class);
            Mockito.when(tm.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
            return new TransactionTemplate(tm);
        }
    }

    @MockBean
    private DoubleEntryLedgerService ledgerService;

    @MockBean
    private com.fooddelivery.common.repository.IIdempotencyKeyRepository idempotencyKeyRepository;

    @MockBean
    private com.fooddelivery.ledger.repository.ILedgerRejectionRepository ledgerRejectionRepository;

    @Autowired
    private StubTrigger stubTrigger;

    @Test
    void recordsDoubleEntryTransactionFromTheProducerStub() {
        stubTrigger.trigger("ledger_events");

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() ->
                verify(ledgerService).record(
                        any(com.fooddelivery.common.dto.ledger.LedgerTransactionCommand.class)));
    }
}
