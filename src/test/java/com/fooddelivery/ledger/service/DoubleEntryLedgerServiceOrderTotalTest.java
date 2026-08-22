package com.fooddelivery.ledger.service;

import com.fooddelivery.common.enums.AccountType;
import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.ledger.entity.LedgerAccount;
import com.fooddelivery.ledger.entity.LedgerEntry;
import com.fooddelivery.ledger.repository.ILedgerAccountRepository;
import com.fooddelivery.ledger.repository.ILedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the meaning of "an order's ledger total" -- the number
 * GET /api/v1/ledger/orders/{orderId}/total returns, which ONDCIntegrationService reports to a
 * settlement counterparty. A wrong number here becomes a wrong financial claim to an external party.
 *
 * These run against a real database because the entire risk lives in the JPQL; a Mockito test of a
 * repository method asserts nothing about whether the sum is right. The contract test in
 * ContractVerifierTest mocks this service, so it proves the endpoint exists and serialises -- not
 * that the arithmetic is correct. That is this file's job.
 *
 * Entries are written directly rather than through recordTransaction. Driving the real method here
 * would pull in pessimistic locks and balance arithmetic that Hibernate renders in PostgreSQL-only
 * SQL, and none of that is what these tests are about. The helper mirrors exactly what
 * recordTransaction writes -- a DEBIT against the source account, a CREDIT against the target, same
 * amount, same transaction id -- so the shape under test is the shape production creates.
 */
@DataJpaTest
@Import(DoubleEntryLedgerService.class)
@TestPropertySource(properties = {
        // Same collision every service has: LedgerApplication and CommonLibrary's OutboxConfiguration
        // both declare @EnableJpaRepositories over com.fooddelivery.common.outbox.repository.
        "spring.main.allow-bean-definition-overriding=true",
        "spring.redis.enabled=false",
        // application.yml pins spring.jpa.properties.hibernate.dialect=PostgreSQLDialect, and that
        // key outranks database-platform.
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
class DoubleEntryLedgerServiceOrderTotalTest {

    private static final UUID PLATFORM_OWNER = new UUID(0, 0);
    private static final UUID CUSTOMER_OWNER = UUID.fromString("6b1d3c22-9f45-4a7e-8c11-2d4e6f8a9b02");

    /*
     * CommonLibrary's OutboxConfiguration component-scans OutboxProcessor into every context, and it
     * needs a KafkaTemplate that @DataJpaTest does not autoconfigure. Nothing here publishes.
     */
    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    @org.springframework.boot.test.mock.mockito.MockBean
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Autowired private DoubleEntryLedgerService ledgerService;
    @Autowired private ILedgerEntryRepository entryRepository;
    @Autowired private ILedgerAccountRepository accountRepository;

    private UUID platformAccountId;
    private UUID customerAccountId;

    @BeforeEach
    void createAccounts() {
        platformAccountId = saveAccount(PLATFORM_OWNER, AccountType.PLATFORM);
        customerAccountId = saveAccount(CUSTOMER_OWNER, AccountType.CUSTOMER);
    }

    private UUID saveAccount(UUID ownerId, AccountType ownerType) {
        LedgerAccount account = LedgerAccount.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .ownerType(ownerType)
                .balance(new BigDecimal("10000.00"))
                .lockVersion(0)
                .build();
        return accountRepository.save(account).getId();
    }

    private void saveEntry(UUID transactionId, UUID referenceId, UUID accountId,
                           TransactionDirection direction, String amount, ChargeCategory category) {
        entryRepository.save(LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(transactionId)
                .referenceId(referenceId)
                .accountId(accountId)
                .direction(direction)
                .category(category)
                .amount(new BigDecimal(amount))
                .createdAt(LocalDateTime.now())
                .build());
    }

    /** Mirrors recordTransaction for a customer paying the platform: debit customer, credit platform. */
    private void customerPaysPlatform(UUID orderRef, String amount, ChargeCategory category) {
        UUID txId = UUID.randomUUID();
        saveEntry(txId, orderRef, customerAccountId, TransactionDirection.DEBIT, amount, category);
        saveEntry(txId, orderRef, platformAccountId, TransactionDirection.CREDIT, amount, category);
    }

    /** Mirrors recordTransaction for a refund: debit platform, credit customer. */
    private void platformRefundsCustomer(UUID orderRef, String amount) {
        UUID txId = UUID.randomUUID();
        saveEntry(txId, orderRef, platformAccountId, TransactionDirection.DEBIT, amount, ChargeCategory.REFUND);
        saveEntry(txId, orderRef, customerAccountId, TransactionDirection.CREDIT, amount, ChargeCategory.REFUND);
    }

    /**
     * The single most important assertion here. Every transaction writes TWO rows -- a DEBIT against
     * the customer and a CREDIT against the platform, both for 100.50. Summing all entries for the
     * reference would report 201.00. The total must be 100.50.
     */
    @Test
    void doesNotDoubleCountTheTwoSidesOfADoubleEntry() {
        UUID orderRef = UUID.randomUUID();
        customerPaysPlatform(orderRef, "100.50", ChargeCategory.FOOD_COST);

        assertThat(entryRepository.findByReferenceId(orderRef))
                .as("a double entry writes both a debit and a credit")
                .hasSize(2);

        assertThat(ledgerService.getOrderLedgerTotal(orderRef))
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("100.50"));
    }

    /** One order produces several transactions sharing a reference: food, delivery, tax. */
    @Test
    void sumsEveryTransactionSharingTheOrderReference() {
        UUID orderRef = UUID.randomUUID();
        customerPaysPlatform(orderRef, "100.50", ChargeCategory.FOOD_COST);
        customerPaysPlatform(orderRef, "30.00", ChargeCategory.DELIVERY_FEE);
        customerPaysPlatform(orderRef, "19.50", ChargeCategory.TAX);

        assertThat(ledgerService.getOrderLedgerTotal(orderRef))
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("150.00"));
    }

    /** A different order's money must not leak in, even against the same platform account. */
    @Test
    void isScopedToOneOrderReference() {
        UUID thisOrder = UUID.randomUUID();
        UUID otherOrder = UUID.randomUUID();
        customerPaysPlatform(thisOrder, "100.50", ChargeCategory.FOOD_COST);
        customerPaysPlatform(otherOrder, "999.99", ChargeCategory.FOOD_COST);

        assertThat(ledgerService.getOrderLedgerTotal(thisOrder))
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("100.50"));
    }

    /**
     * DELIBERATE, AND THE ONE BEHAVIOUR TO SANITY-CHECK: the total is GROSS of refunds.
     *
     * A refund moves money platform -> customer, so it is a DEBIT against the platform account, and
     * only CREDITs are summed. A fully refunded order therefore still reports its original total.
     *
     * If reconciliation should report net, this test is the thing that will fail, and that is the
     * point -- the behaviour is pinned rather than discovered against a counterparty.
     */
    @Test
    void isGrossOfRefunds() {
        UUID orderRef = UUID.randomUUID();
        customerPaysPlatform(orderRef, "100.50", ChargeCategory.FOOD_COST);
        platformRefundsCustomer(orderRef, "100.50");

        assertThat(ledgerService.getOrderLedgerTotal(orderRef))
                .as("refunds are debits against the platform and do not reduce the gross total")
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(new BigDecimal("100.50"));
    }

    /** An unknown reference is 0.00, never null -- the caller does arithmetic on this directly. */
    @Test
    void returnsZeroRatherThanNullForAnUnknownOrder() {
        assertThat(ledgerService.getOrderLedgerTotal(UUID.randomUUID()))
                .isNotNull()
                .usingComparator(BigDecimal::compareTo)
                .isEqualTo(BigDecimal.ZERO);
    }
}
