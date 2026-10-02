package com.fooddelivery.ledger.repository;

import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.ledger.entity.LedgerEntry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The entity's unique constraint against a database: a compound transaction may debit one account
 * several times, but the same leg of a transaction cannot be written twice. The migration
 * V20261002210000 makes the same change in PostgreSQL.
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
class LedgerEntryLegUniquenessTest {

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;
    @org.springframework.boot.test.mock.mockito.MockBean
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Autowired private ILedgerEntryRepository entries;

    private static final UUID TX = UUID.randomUUID();
    private static final UUID CLEARING = UUID.randomUUID();

    private LedgerEntry entry(int leg, UUID account, TransactionDirection direction, ChargeCategory category) {
        return LedgerEntry.builder().id(UUID.randomUUID()).legIndex(leg).transactionId(TX).referenceId(UUID.randomUUID())
                .accountId(account).direction(direction).category(category).amount(new BigDecimal("1.00"))
                .producer("customer-application").createdAt(Instant.now()).build();
    }

    @Test
    void oneTransactionMayDebitTheSameAccountOncePerLeg() {
        entries.saveAndFlush(entry(0, CLEARING, TransactionDirection.DEBIT, ChargeCategory.DELIVERY_FEE));
        entries.saveAndFlush(entry(1, CLEARING, TransactionDirection.DEBIT, ChargeCategory.SGST));
        entries.saveAndFlush(entry(2, CLEARING, TransactionDirection.DEBIT, ChargeCategory.FOOD_COST));

        assertThat(entries.count()).isEqualTo(3);
    }

    @Test
    void theSameLegCannotBeWrittenTwice() {
        entries.saveAndFlush(entry(0, CLEARING, TransactionDirection.DEBIT, ChargeCategory.DELIVERY_FEE));

        assertThatThrownBy(() -> entries.saveAndFlush(entry(0, UUID.randomUUID(), TransactionDirection.DEBIT, ChargeCategory.DELIVERY_FEE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
