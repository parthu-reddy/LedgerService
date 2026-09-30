package com.fooddelivery.ledger.entity;

/**
 * The operator actions that can change a payout's lifecycle.
 *
 * <p>The value is persisted as an immutable audit record rather than inferred from the payout's
 * final state. In particular, FAILED can originate from either DRAFT or APPROVED.
 */
public enum PayoutOperationAction {
    APPROVE,
    MARK_PAID,
    FAIL,
    CANCEL
}
