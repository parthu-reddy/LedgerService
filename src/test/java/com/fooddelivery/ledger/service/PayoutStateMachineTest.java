package com.fooddelivery.ledger.service;

import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.entity.PayoutStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Every pair of payout states, not a sampled few.
 *
 * <p>The previous version walked the happy path and tried two moves out of PAID. It therefore said
 * nothing about the transition that actually loses money — DRAFT straight to PAID, paying a payout
 * nobody approved. Deleting the approval check left it green; found 2026-09-09 performing Phase 5's
 * break-test 3.
 */
class PayoutStateMachineTest {

    /** The only moves the business permits. Everything outside this set must be refused. */
    private static final Set<String> ALLOWED = Set.of(
            "DRAFT->APPROVED",
            "DRAFT->FAILED",
            "DRAFT->CANCELLED",
            "APPROVED->PAID",
            "APPROVED->FAILED");

    private PayoutStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new PayoutStateMachine();
    }

    private static Payout at(PayoutStatus status) {
        Payout payout = new Payout();
        payout.setStatus(status);
        return payout;
    }

    static List<Object[]> everyPair() {
        List<Object[]> pairs = new ArrayList<>();
        for (PayoutStatus from : PayoutStatus.values()) {
            for (PayoutStatus to : PayoutStatus.values()) {
                if (from != to) {
                    pairs.add(new Object[]{from, to});
                }
            }
        }
        return pairs;
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPair")
    void onlyThePermittedTransitionsAreAccepted(PayoutStatus from, PayoutStatus to) {
        Payout payout = at(from);
        String move = from + "->" + to;

        if (ALLOWED.contains(move)) {
            stateMachine.transitionTo(payout, to);
            assertEquals(to, payout.getStatus(), move + " is a permitted move and must be applied");
        } else {
            assertThrows(RuntimeException.class, () -> stateMachine.transitionTo(payout, to),
                    move + " must be refused; accepting it lets a payout reach " + to
                    + " without passing through the states that authorise it");
            assertEquals(from, payout.getStatus(),
                    "a refused transition must leave the payout where it was");
        }
    }

    /** The one that pays out money nobody approved. Called out on its own so a failure names it. */
    @Test
    void aDraftPayoutCannotBePaidWithoutApproval() {
        Payout payout = at(PayoutStatus.DRAFT);
        assertThrows(IllegalStateException.class, () -> stateMachine.transitionTo(payout, PayoutStatus.PAID));
        assertEquals(PayoutStatus.DRAFT, payout.getStatus());
    }

    /** PAID, FAILED and CANCELLED are the end of the line. */
    @ParameterizedTest
    @EnumSource(value = PayoutStatus.class, names = {"PAID", "FAILED", "CANCELLED"})
    void terminalStatesAcceptNothing(PayoutStatus terminal) {
        for (PayoutStatus to : EnumSet.complementOf(EnumSet.of(terminal))) {
            Payout payout = at(terminal);
            assertThrows(RuntimeException.class, () -> stateMachine.transitionTo(payout, to),
                    terminal + " is terminal but accepted a move to " + to);
        }
    }

    /** Re-issuing the state a payout already holds is a no-op, so a retried call is harmless. */
    @ParameterizedTest
    @EnumSource(PayoutStatus.class)
    void repeatingTheCurrentStateIsANoOp(PayoutStatus status) {
        Payout payout = at(status);
        stateMachine.transitionTo(payout, status);
        assertEquals(status, payout.getStatus());
    }
}
