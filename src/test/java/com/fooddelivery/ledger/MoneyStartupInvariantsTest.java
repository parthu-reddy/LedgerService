package com.fooddelivery.ledger;

import com.fooddelivery.common.constants.LedgerAccounts;
import com.fooddelivery.common.enums.PaymentGateway;
import com.fooddelivery.ledger.service.MoneyStartupInvariants;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The invariants that must hold before this service serves traffic.
 *
 * <p>This class previously asserted that the context loads and that the {@code test} profile is
 * active — a tautology, since the test sets it. None of the four properties plan §7.3 named were
 * checked, and no code checked them either.
 */
public class MoneyStartupInvariantsTest {

    private static final String[] NO_PROFILES = new String[0];

    @Test
    void aCorrectlyConfiguredDeploymentPasses() {
        assertEquals(List.of(), MoneyStartupInvariants.check("INR", NO_PROFILES));
    }

    /** WalletService.createWallet falls back to this value, so a wrong one mints wrong wallets. */
    @Test
    void aCurrencyOtherThanINRIsRefused() {
        List<String> violations = MoneyStartupInvariants.check("USD", NO_PROFILES);
        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.get(0).contains("not INR"), violations.get(0));
    }

    @Test
    void aMissingCurrencyIsRefused() {
        assertFalse(MoneyStartupInvariants.check(null, NO_PROFILES).isEmpty());
        assertFalse(MoneyStartupInvariants.check("", NO_PROFILES).isEmpty());
    }

    /** prod,debug registers a real and a mock strategy for one gateway: P-20. */
    @Test
    void prodTogetherWithDebugIsRefused() {
        List<String> violations = MoneyStartupInvariants.check("INR", new String[]{"prod", "debug"});
        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.get(0).contains("real and a mock"), violations.get(0));
    }

    @Test
    void debugAloneIsFineBecauseThatIsDev() {
        assertEquals(List.of(), MoneyStartupInvariants.check("INR", new String[]{"dev", "debug"}));
    }

    /**
     * The property the check exists to hold: the five fixed accounts are five different accounts.
     * PayoutService addressed BANK and PAYOUT_IN_TRANSIT with the same all-zeros literal that
     * PLATFORM_CLEARING uses, so cash remittances and payouts landed in different bank accounts.
     */
    @Test
    void theFixedLedgerAccountIdsAreDistinct() {
        List<UUID> fixed = List.of(LedgerAccounts.PLATFORM_CLEARING, LedgerAccounts.PLATFORM_REVENUE,
                LedgerAccounts.TAX_PAYABLE, LedgerAccounts.BANK, LedgerAccounts.PAYOUT_IN_TRANSIT);
        assertEquals(fixed.size(), new HashSet<>(fixed).size(), "fixed account ids collide: " + fixed);
    }

    @Test
    void everyGatewayResolvesToItsOwnAccountAndNoneCollidesWithAFixedOne() {
        Set<UUID> fixed = Set.of(LedgerAccounts.PLATFORM_CLEARING, LedgerAccounts.PLATFORM_REVENUE,
                LedgerAccounts.TAX_PAYABLE, LedgerAccounts.BANK, LedgerAccounts.PAYOUT_IN_TRANSIT);
        Set<UUID> seen = new HashSet<>();
        for (PaymentGateway gateway : PaymentGateway.values()) {
            UUID id = LedgerAccounts.gatewayOwnerId(gateway);
            assertTrue(seen.add(id), gateway + " shares a receivable account with another gateway");
            assertFalse(fixed.contains(id), gateway + " derives a fixed account id");
        }
        assertEquals(PaymentGateway.values().length, seen.size());
    }

    /**
     * Under prod a violation must stop the service; outside prod it must not, so a developer can
     * still bring the stack up and look at what is wrong.
     */
    @Test
    void aViolationIsFatalUnderProdAndLoudEverywhereElse() {
        org.springframework.mock.env.MockEnvironment prod = new org.springframework.mock.env.MockEnvironment();
        prod.setActiveProfiles("prod");
        MoneyStartupInvariants underProd = new MoneyStartupInvariants(prod);
        org.springframework.test.util.ReflectionTestUtils.setField(underProd, "platformCurrency", "USD");
        assertThrows(IllegalStateException.class, underProd::assertInvariants);

        org.springframework.mock.env.MockEnvironment dev = new org.springframework.mock.env.MockEnvironment();
        dev.setActiveProfiles("dev");
        MoneyStartupInvariants underDev = new MoneyStartupInvariants(dev);
        org.springframework.test.util.ReflectionTestUtils.setField(underDev, "platformCurrency", "USD");
        underDev.assertInvariants(); // must not throw
    }

    /** A correctly configured prod deployment starts. */
    @Test
    void aCorrectProdDeploymentStarts() {
        org.springframework.mock.env.MockEnvironment prod = new org.springframework.mock.env.MockEnvironment();
        prod.setActiveProfiles("prod");
        MoneyStartupInvariants underProd = new MoneyStartupInvariants(prod);
        org.springframework.test.util.ReflectionTestUtils.setField(underProd, "platformCurrency", "INR");
        underProd.assertInvariants();
    }

    /** Case and whitespace must not create a second account for the same gateway. */
    @Test
    void aGatewayNameIsNormalisedBeforeItIsHashed() {
        assertEquals(LedgerAccounts.gatewayOwnerId(PaymentGateway.RAZORPAY),
                LedgerAccounts.gatewayOwnerId("  razorpay  "));
    }
}
