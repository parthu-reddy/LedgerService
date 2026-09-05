package com.fooddelivery.ledger.service;

import com.fooddelivery.ledger.entity.Payout;
import com.fooddelivery.ledger.entity.PayoutStatus;
import org.springframework.stereotype.Component;

@Component
public class PayoutStateMachine {

    public void transitionTo(Payout payout, PayoutStatus newStatus) {
        PayoutStatus current = payout.getStatus();

        if (current == newStatus) {
            return;
        }

        switch (newStatus) {
            case APPROVED:
                if (current != PayoutStatus.DRAFT) {
                    throw new IllegalStateException("Can only approve DRAFT payouts");
                }
                break;
            case PAID:
                if (current != PayoutStatus.APPROVED) {
                    throw new IllegalStateException("Can only pay APPROVED payouts");
                }
                break;
            case FAILED:
                if (current != PayoutStatus.DRAFT && current != PayoutStatus.APPROVED) {
                    throw new IllegalStateException("Can only fail DRAFT or APPROVED payouts");
                }
                break;
            case CANCELLED:
                if (current != PayoutStatus.DRAFT) {
                    throw new IllegalStateException("Can only cancel DRAFT payouts");
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown status transition to " + newStatus);
        }

        payout.setStatus(newStatus);
    }
}
