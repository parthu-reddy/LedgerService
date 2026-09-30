package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.LedgerRejection;
import com.fooddelivery.ledger.config.LedgerJpaConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes the conditional resolution query against Hibernate. The sequential calls model the
 * two database outcomes available to concurrent callers: the first changes an unresolved row and
 * every later caller receives a zero-row compare-and-set result. PostgreSQL supplies the row lock
 * while evaluating the predicate in production.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(LedgerJpaConfig.class)
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.redis.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:ledger_rejection_resolution;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=CREATE DOMAIN IF NOT EXISTS JSONB AS JSON",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
class LedgerRejectionResolutionPersistenceTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    @org.springframework.boot.test.mock.mockito.MockBean
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Autowired
    private ILedgerRejectionRepository rejectionRepository;

    @Test
    void resolvesOnlyTheFirstDecisionAndKeepsItsAuditFields() {
        UUID id = UUID.randomUUID();
        rejectionRepository.saveAndFlush(LedgerRejection.builder()
                .id(id)
                .eventId("ledger-event-1")
                .producer("customer-application")
                .payload("{\"transactionId\":\"ledger-event-1\"}")
                .reason("test rejection")
                .createdAt(Instant.parse("2026-09-30T09:00:00Z"))
                .build());

        Instant firstAt = Instant.parse("2026-09-30T10:00:00Z");
        assertThat(rejectionRepository.resolveIfUnresolved(id, firstAt, "first-admin", "first decision"))
                .isEqualTo(1);
        assertThat(rejectionRepository.resolveIfUnresolved(id, Instant.parse("2026-09-30T10:01:00Z"),
                "second-admin", "second decision"))
                .isZero();

        LedgerRejection stored = rejectionRepository.findById(id).orElseThrow();
        assertThat(stored.getResolvedAt()).isEqualTo(firstAt);
        assertThat(stored.getResolvedBy()).isEqualTo("first-admin");
        assertThat(stored.getResolutionNote()).isEqualTo("first decision");
    }
}
