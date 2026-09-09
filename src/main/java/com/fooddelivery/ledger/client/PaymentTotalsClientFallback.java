package com.fooddelivery.ledger.client;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Throws, like every other reconciliation client fallback.
 *
 * <p>This returned {@code Collections.emptyMap()}. {@code checkGatewayVsLedger} reads
 * {@code totals.getOrDefault("capturedAmount", ZERO)}, so an unreachable PaymentGatewayIntegration
 * became "the gateway captured nothing today" — compared against the ledger's real figures, that
 * files a GATEWAY_VS_LEDGER break for <em>every</em> gateway, and because nothing threw the run was
 * marked SUCCESS rather than PARTIAL. Operators would be paged to investigate a discrepancy that
 * only meant one service was down, and the day's captures would never actually be reconciled.
 *
 * <p>Its two siblings, {@link OrderTotalsClientFallback} and {@link WalletBalancesClientFallback},
 * have always thrown. This one was the odd one out; found 2026-09-09 by sweeping money code for
 * stub-bodied methods after the same defect was fixed in OrderTotalsClientFallback in isolation.
 * {@code executeRun} catches this and marks the run PARTIAL, which is the honest outcome: the check
 * did not run, rather than ran and disagreed.
 */
@Component
public class PaymentTotalsClientFallback implements PaymentTotalsClient {
    @Override
    public Map<String, BigDecimal> getDailyTotals(LocalDate date, String gatewayName) {
        throw new ReconciliationClientException("PaymentGatewayIntegration unavailable");
    }
}
