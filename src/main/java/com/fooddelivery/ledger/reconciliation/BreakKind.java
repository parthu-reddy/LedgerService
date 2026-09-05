package com.fooddelivery.ledger.reconciliation;

public enum BreakKind {
    GATEWAY_VS_LEDGER,
    ORDERS_VS_CLEARING,
    WALLET_VS_LEDGER,
    PAYABLE_VS_ORDERS,
    DOUBLE_ENTRY,
    STUCK
}
