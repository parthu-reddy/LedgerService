package com.fooddelivery.ledger.service;

import com.fooddelivery.common.constants.LedgerAccounts;
import com.fooddelivery.common.enums.PaymentGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Refuses to serve traffic if the deployment is not one money can be moved in.
 *
 * <p>Plan §7.3 called for this and it was never written: {@code MoneyStartupInvariantsTest} asserted
 * that the context loads and that the {@code test} profile is active — the second being a tautology,
 * since the test sets it.
 *
 * <p>A money service that starts with the wrong currency or an unresolvable account id is worse than
 * one that refuses to start: it books entries that have to be unpicked by hand afterwards.
 */
@Component
@Slf4j
public class MoneyStartupInvariants {

    public static final String REQUIRED_CURRENCY = "INR";

    private final Environment environment;

    @Value("${platform.default-currency:INR}")
    private String platformCurrency;

    public MoneyStartupInvariants(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void assertInvariants() {
        List<String> violations = check(platformCurrency, environment.getActiveProfiles());
        if (violations.isEmpty()) {
            log.info("Money startup invariants hold: currency={}, {} fixed accounts resolve, {} gateways addressable",
                    platformCurrency, 5, PaymentGateway.values().length);
            return;
        }

        String report = "Money startup invariants failed:\n  " + String.join("\n  ", violations);
        if (List.of(environment.getActiveProfiles()).contains("prod")) {
            // Refuse to take money on a deployment that is not configured to move it correctly.
            throw new IllegalStateException(report);
        }
        // Outside prod this is a loud warning rather than a dead service: a developer bringing the
        // stack up to try something should not be blocked by a misconfiguration that cannot reach
        // real money, and a service that refuses to boot is far harder to diagnose than a log line.
        log.error("{}\n  (not fatal outside the prod profile -- this WOULD refuse to start in production)", report);
    }

    /** Pure so it can be exercised without booting a context. */
    public static List<String> check(String currency, String[] activeProfiles) {
        List<String> violations = new ArrayList<>();

        if (!REQUIRED_CURRENCY.equals(currency)) {
            violations.add("platform.default-currency is '" + currency + "', not " + REQUIRED_CURRENCY
                    + ". Every ledger account and wallet is created in this currency.");
        }

        // The fixed accounts must be distinct: PayoutService addressed BANK and PAYOUT_IN_TRANSIT
        // with the same all-zeros literal that PLATFORM_CLEARING uses.
        List<UUID> fixed = List.of(LedgerAccounts.PLATFORM_CLEARING, LedgerAccounts.PLATFORM_REVENUE,
                LedgerAccounts.TAX_PAYABLE, LedgerAccounts.BANK, LedgerAccounts.PAYOUT_IN_TRANSIT);
        Set<UUID> distinct = new HashSet<>(fixed);
        if (distinct.size() != fixed.size()) {
            violations.add("the fixed ledger account ids are not distinct: " + fixed);
        }

        // Every gateway must hash to its own receivable account, and none may collide with a fixed one.
        Set<UUID> gatewayAccounts = new HashSet<>();
        for (PaymentGateway gateway : PaymentGateway.values()) {
            UUID id = LedgerAccounts.gatewayOwnerId(gateway);
            if (!gatewayAccounts.add(id)) {
                violations.add("two gateways derive the same receivable account id: " + gateway);
            }
            if (distinct.contains(id)) {
                violations.add("gateway " + gateway + " derives a fixed account id: " + id);
            }
        }

        for (String profile : activeProfiles) {
            if ("debug".equals(profile) && List.of(activeProfiles).contains("prod")) {
                violations.add("both 'prod' and 'debug' are active, which registers a real and a mock "
                        + "gateway strategy for the same gateway");
            }
        }

        return violations;
    }
}
