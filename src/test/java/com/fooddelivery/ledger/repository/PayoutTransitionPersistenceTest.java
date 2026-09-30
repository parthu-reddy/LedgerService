package com.fooddelivery.ledger.repository;

import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.entity.PayoutOperation;
import com.fooddelivery.ledger.entity.PayoutOperationAction;
import com.fooddelivery.ledger.entity.PayoutStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes the transition repositories against Hibernate rather than a mock. H2 cannot run the
 * PostgreSQL migration or reproduce PostgreSQL row-lock scheduling, but this pins the JPA mapping
 * and the locked lookup that production uses before a terminal transition.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.redis.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
class PayoutTransitionPersistenceTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    @org.springframework.boot.test.mock.mockito.MockBean
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Autowired
    private PayoutRepository payoutRepository;

    @Autowired
    private PayoutOperationRepository payoutOperationRepository;

    @Test
    void persistsAnImmutableOperationAndUsesTheLockedPayoutLookup() {
        Instant now = Instant.parse("2026-09-29T10:00:00Z");
        UUID payoutId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Payout payout = payoutRepository.saveAndFlush(Payout.builder()
                .id(payoutId)
                .payeeType("RESTAURANT")
                .payeeId(UUID.randomUUID())
                .payeeDisplayName("Test Restaurant")
                .periodFrom(now.minusSeconds(86_400))
                .periodTo(now)
                .amount(new BigDecimal("125.50"))
                .currency("INR")
                .status(PayoutStatus.DRAFT)
                .beneficiarySnapshot("{}")
                .createdBy(actorId)
                .idempotencyKey("create-payout-key")
                .ledgerTransactionId(UUID.randomUUID())
                .createdAt(now)
                .updatedAt(now)
                .build());

        assertThat(payout.getLockVersion()).isZero();
        assertThat(payoutRepository.findByIdForUpdate(payoutId))
                .map(Payout::getStatus)
                .contains(PayoutStatus.DRAFT);

        PayoutOperation operation = PayoutOperation.builder()
                .id(UUID.randomUUID())
                .payoutId(payoutId)
                .action(PayoutOperationAction.APPROVE)
                .idempotencyKey("approve-payout-key")
                .requestHash("a".repeat(64))
                .actorId(actorId)
                .statusBefore(PayoutStatus.DRAFT)
                .statusAfter(PayoutStatus.APPROVED)
                .outcome("APPLIED")
                .createdAt(now)
                .build();
        PayoutOperation persistedOperation = payoutOperationRepository.saveAndFlush(operation);

        assertThat(payoutOperationRepository.findByIdempotencyKey("approve-payout-key"))
                .map(PayoutOperation::getId)
                .contains(persistedOperation.getId());
    }
}
