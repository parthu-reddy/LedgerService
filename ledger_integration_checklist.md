# Ledger Integration Scenarios Checklist

This checklist covers all potential integration scenarios, edge cases, and failure modes between the Customer/Order workflows and the asynchronous Double-Entry Ledger System.

## 1. Event Publishing & Consumption (Kafka/Outbox)
- [x] **Scenario 1.1**: Outbox poller crashes midway through publishing. Does the retry mechanism handle idempotency correctly? (Database unique constraint on outbox handles this on publish, Ledger handles this on consume via transaction_id uniqueness).
- [x] **Scenario 1.2**: Kafka topic partition rebalancing. Does LedgerService handle duplicated events gracefully? (Handled implicitly via `existsByTransactionId` check in DoubleEntryLedgerService).
- [x] **Scenario 1.3**: LedgerService is down for an extended period. Are events queued and processed upon recovery without losing strict chronological ordering if needed, or does order not matter because of double-entry rules? (Order does not matter due to strict double-entry logic; Kafka persists the events).
- [x] **Scenario 1.4**: Event payload structure changes (e.g., adding `ChargeCategory`). Does deserialization fail on older in-flight events? (Handled via `@JsonIgnoreProperties(ignoreUnknown = true)` default in ObjectMapper and backward-compatible fields).
- [x] **Scenario 1.5**: Outbox Table Bloat. Is there a background cron job to prune or archive `PROCESSED` events that are older than 7 days?

## 2. Double-Entry Accounting Strictness
- [x] **Scenario 2.1**: A transaction request contains a null amount or negative amount. Is it strictly rejected?
- [x] **Scenario 2.2**: The debit and credit accounts are identical. Does the system prevent this (a self-transfer that inflates transaction volume)?
- [x] **Scenario 2.3**: Concurrent identical transaction requests (same `transaction_id`). Does the unique constraint on `(transaction_id, direction)` catch it before corrupting the balance?
- [x] **Scenario 2.4**: Transaction currency mismatch (e.g., one service sends USD, another INR). Is currency explicitly validated or implicitly assumed? (Implicitly assumed in this single-currency system).
- [x] **Scenario 2.5**: The total sum of all DEBIT entries vs CREDIT entries across the entire database. Do they always sum to zero?

## 3. Order & Payment Lifecycle
- [x] **Scenario 3.1**: Customer creates an order, payment fails. Is a ledger entry accidentally created? (No, `PAYMENT_FAILED` cancels the order, no ledger tx).
- [x] **Scenario 3.2**: Customer creates an order, payment succeeds, but the restaurant rejects it. Does the refund trigger a proper inverse ledger transaction with the correct category? (Yes, `PAYMENT_REFUNDED` handled).
- [x] **Scenario 3.3**: Partial refunds (e.g., item unavailable). Are partial amounts correctly recorded in the ledger, leaving the platform fee intact if applicable? (Yes, partial refund endpoint implemented in Admin API which emits `PAYMENT_PARTIALLY_REFUNDED`).
- [x] **Scenario 3.4**: Delayed approval (Order action delayed). Does the ledger entry wait until finalization, or is it created optimistically and then reversed? (No ledger entries for payouts are made until `DELIVERED`).
- [x] **Scenario 3.5**: Order canceled by customer before pickup. Does the cancellation trigger the correct ledger reversal? (Yes, triggers refund).
- [x] **Scenario 3.6**: Delivery Executive payout calculation. Does it accurately reflect the delivery fee minus platform cut? (Yes, `DynamicPricingService` splits appropriately).
- [x] **Scenario 3.7**: Restaurant payout calculation. Does it accurately reflect the food cost minus platform commission? (Yes, handled by `DynamicPricingService`).

## 4. Security & Authorization
- [x] **Scenario 4.1**: Internal APIs bypassing gateway. Can an attacker hit the LedgerService REST API directly?
- [x] **Scenario 4.2**: Cross-tenant data leakage. Can a restaurant view another restaurant's ledger account? (No, `LedgerController` enforces `ownerId` match against Principal).
- [x] **Scenario 4.3**: Kafka event spoofing. Can a malicious payload be injected into Kafka? (Kafka requires internal VPC auth).

## 5. Schema & Initialization
- [x] **Scenario 5.1**: Oracle DDL validation. Are there any native Postgres constraints/queries left? (All reviewed).
- [x] **Scenario 5.2**: Missing `@Column(name="...")` mappings on new entities in `LedgerService` causing Oracle table name collisions. (Handled via script/manual check).
- [x] **Scenario 5.3**: Missing default data. Are the system `PLATFORM` ledger accounts auto-initialized, or will the first transaction crash because the account doesn't exist? (`getOrCreateAccount` auto-initializes).

## 6. Payouts & Settlement
- [x] **Scenario 6.1**: Payout Reconciliation. How does `PaymentService` bulk-query negative platform balances (meaning the platform owes the restaurant/driver money) at the end of the payout cycle? Is an internal API missing? (Added `/api/v1/ledger/payouts/pending` to `LedgerController`).
- [x] **Scenario 6.2**: Admin settlement of pending payouts. How is a negative platform liability cleared in the ledger when the finance team completes a fiat payout? (Added `POST /api/v1/ledger/payouts/settle` to `LedgerController` to debit the restaurant/driver balance and credit the platform).
