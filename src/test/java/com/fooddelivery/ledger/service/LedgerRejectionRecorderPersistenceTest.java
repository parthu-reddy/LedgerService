package com.fooddelivery.ledger.service;

import com.fooddelivery.common.entity.IdempotencyKey;
import com.fooddelivery.common.repository.IIdempotencyKeyRepository;
import com.fooddelivery.ledger.config.LedgerJpaConfig;
import com.fooddelivery.ledger.repository.ILedgerRejectionRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A semantic rejection has to survive the rollback of the booking transaction that discovered it.
 * This exercises the {@code REQUIRES_NEW} boundary against a real JPA transaction manager.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({LedgerJpaConfig.class, LedgerRejectionRecorder.class})
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.redis.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:ledger_rejection_recorder;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
class LedgerRejectionRecorderPersistenceTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    @org.springframework.boot.test.mock.mockito.MockBean
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private LedgerRejectionRecorder recorder;

    @Autowired
    private IIdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private ILedgerRejectionRepository rejectionRepository;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void recordsSemanticRejectionAfterTheBookingTransactionRollsBack() {
        String rolledBackKey = "booking:" + UUID.randomUUID();
        String terminalKey = "processed_event:" + UUID.randomUUID();
        String eventId = "ledger-event-" + UUID.randomUUID();

        transactionTemplate.execute(status -> {
            idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(rolledBackKey));
            recorder.record(terminalKey, eventId, "customer-application", "{\"id\":\"1\"}",
                    "INSUFFICIENT_FUNDS");
            status.setRollbackOnly();
            return null;
        });

        assertThat(idempotencyKeyRepository.findById(rolledBackKey)).isEmpty();
        assertThat(idempotencyKeyRepository.findById(terminalKey)).isPresent();
        recorder.record(terminalKey, eventId, "customer-application", "{\"id\":\"1\"}", "Repeated delivery");
        assertThat(rejectionRepository.findAll().stream().filter(r -> eventId.equals(r.getEventId())).toList())
                .hasSize(1);
        assertThat(rejectionRepository.findAll())
                .anySatisfy(rejection -> {
                    assertThat(rejection.getEventId()).isEqualTo(eventId);
                    assertThat(rejection.getProducer()).isEqualTo("customer-application");
                    assertThat(rejection.getReason()).isEqualTo("INSUFFICIENT_FUNDS");
                });
    }
}
