package com.fooddelivery.ledger.service;

import com.fooddelivery.common.enums.ChargeCategory;
import com.fooddelivery.common.enums.LedgerAccountType;
import com.fooddelivery.common.enums.TransactionDirection;
import com.fooddelivery.common.time.TimeWindow;
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
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what "an outlet's clawbacks in a window" means: the CLAWBACK debits on that outlet's
 * RESTAURANT_PAYABLE account booked in {@code [from, to)}. The restaurant earnings summary shows
 * this number, and until 2026-09-26 it was hardcoded to zero.
 *
 * <p>Run against a real database because the whole risk is in the JPQL; the contract test mocks this
 * service. Entries are written directly, in the shape recordTransaction writes them, as in
 * {@link DoubleEntryLedgerServiceOrderTotalTest}.
 */
@DataJpaTest
@Import(DoubleEntryLedgerService.class)
@TestPropertySource(properties = {
        "spring.main.allow-bean-definition-overriding=true",
        "spring.redis.enabled=false",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false"
})
class DoubleEntryLedgerServiceCategoryTotalTest {

    private static final UUID OUTLET = UUID.fromString("0c9a8b7d-1e2f-4a3b-8c4d-5e6f7a8b9c01");
    private static final UUID OTHER_OUTLET = UUID.fromString("0c9a8b7d-1e2f-4a3b-8c4d-5e6f7a8b9c02");
    private static final UUID PLATFORM = new UUID(0, 0);

    /** A London week over the fall-back, 169 hours, as the outlet's calendar makes it. */
    private static final TimeWindow WEEK = new TimeWindow(Instant.parse("2026-10-18T23:00:00Z"), Instant.parse("2026-10-26T00:00:00Z"));

    @org.springframework.boot.test.mock.mockito.MockBean
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    @org.springframework.boot.test.mock.mockito.MockBean
    private io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Autowired private DoubleEntryLedgerService ledgerService;
    @Autowired private ILedgerEntryRepository entryRepository;
    @Autowired private ILedgerAccountRepository accountRepository;

    private UUID outletPayable;
    private UUID otherOutletPayable;
    private UUID outletIdAsDriverPayable;
    private UUID platformClearing;

    @BeforeEach
    void createAccounts() {
        outletPayable = saveAccount(OUTLET, LedgerAccountType.RESTAURANT_PAYABLE);
        otherOutletPayable = saveAccount(OTHER_OUTLET, LedgerAccountType.RESTAURANT_PAYABLE);
        // Same owner id on another account type: must not be counted as the outlet's.
        outletIdAsDriverPayable = saveAccount(OUTLET, LedgerAccountType.DRIVER_PAYABLE);
        platformClearing = saveAccount(PLATFORM, LedgerAccountType.PLATFORM_CLEARING);
    }

    private UUID saveAccount(UUID ownerId, LedgerAccountType ownerType) {
        return accountRepository.save(LedgerAccount.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .ownerType(ownerType)
                .balance(new BigDecimal("10000.00"))
                .currency("INR")
                .createdAt(Instant.now())
                .kind(LedgerAccountType.Kind.PAYABLE)
                .lockVersion(0)
                .build()).getId();
    }

    private void saveEntry(UUID accountId, TransactionDirection direction, ChargeCategory category, String amount, Instant at) {
        entryRepository.save(LedgerEntry.builder()
                .id(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .referenceId(UUID.randomUUID())
                .accountId(accountId)
                .direction(direction)
                .category(category)
                .amount(new BigDecimal(amount))
                .createdAt(at)
                .producer("TEST_PRODUCER")
                .build());
    }

    /** Mirrors LedgerBookkeeper.bookRefund's restaurant-fault leg: debit the outlet's payable, credit platform clearing. */
    private void clawedBack(UUID payableAccount, String amount, Instant at) {
        saveEntry(payableAccount, TransactionDirection.DEBIT, ChargeCategory.CLAWBACK, amount, at);
        saveEntry(platformClearing, TransactionDirection.CREDIT, ChargeCategory.CLAWBACK, amount, at);
    }

    private BigDecimal outletClawbacks() {
        return ledgerService.getCategoryTotal(LedgerAccountType.RESTAURANT_PAYABLE, OUTLET,
                ChargeCategory.CLAWBACK, TransactionDirection.DEBIT, WEEK);
    }

    @Test
    void sumsTheOutletsClawbacksBookedInTheWindowIncludingItsFirstInstantButNotItsLast() {
        clawedBack(outletPayable, "11.00", WEEK.from().minusMillis(1));
        clawedBack(outletPayable, "40.00", WEEK.from());
        clawedBack(outletPayable, "2.50", WEEK.to().minusMillis(1));
        clawedBack(outletPayable, "900.00", WEEK.to());

        assertThat(outletClawbacks()).isEqualByComparingTo("42.50");
    }

    @Test
    void countsOnlyClawbackDebitsOnThisOutletsPayable() {
        Instant inside = WEEK.from().plusSeconds(3600);
        clawedBack(outletPayable, "40.00", inside);
        // Earnings and fees on the same account, in the same window: not clawbacks.
        saveEntry(outletPayable, TransactionDirection.CREDIT, ChargeCategory.FOOD_COST, "500.00", inside);
        saveEntry(outletPayable, TransactionDirection.DEBIT, ChargeCategory.PLATFORM_FIXED_FEE, "10.00", inside);
        saveEntry(outletPayable, TransactionDirection.DEBIT, ChargeCategory.PLATFORM_BONUS, "7.00", inside);
        // Money credited back to the outlet under CLAWBACK (nothing books this today) is not money clawed back.
        saveEntry(outletPayable, TransactionDirection.CREDIT, ChargeCategory.CLAWBACK, "5.00", inside);
        // Another outlet's clawback, and a clawback on a different account type that shares the owner id.
        clawedBack(otherOutletPayable, "300.00", inside);
        clawedBack(outletIdAsDriverPayable, "60.00", inside);

        assertThat(outletClawbacks()).isEqualByComparingTo("40.00");
    }

    @Test
    void nothingClawedBackIsZero() {
        assertThat(outletClawbacks()).isEqualByComparingTo("0");
    }
}
