# Ledger Payout Flow

Currently, the exact payout logic (the percentage and flat fee) is **hardcoded in the application code**, not stored in a database or external configuration file. 

Specifically, you can find this logic in `PickedUpState.java` inside the `CustomerApplication`:
[PickedUpState.java](file:///Users/parthureddy/Documents/Food%20Delivery.nosync/CustomerApplication/src/main/java/com/fooddelivery/order/service/state/impl/PickedUpState.java#L26-L29)

```java
        // Ledger accounting
        BigDecimal total = order.getTotalAmount();
        BigDecimal restPayout = total.multiply(new BigDecimal("0.80")); // 80% to Restaurant
        BigDecimal driverPayout = new BigDecimal("50.00");              // Flat Rs 50 to Driver
```

Here is a detailed flow diagram showing how the funds move from the moment a customer pays to the moment the payouts are distributed.

```mermaid
sequenceDiagram
    autonumber
    participant Customer
    participant CustomerApp as Customer Application
    participant PG as Payment Gateway (Vyapar)
    participant Ledger as DoubleEntryLedger
    participant Restaurant
    participant Driver as Delivery Executive

    %% Payment Flow
    Customer->>CustomerApp: Places Order & Initiates Payment
    CustomerApp->>PG: Creates Payment Intent
    PG-->>CustomerApp: Returns Payment Link/Intent
    Customer->>PG: Completes Payment (e.g. ₹500)
    PG-->>CustomerApp: Webhook: Payment Success
    
    %% Initial Ledger Entry
    rect rgb(200, 220, 240)
        Note right of Ledger: [1] Initial Payment Capture
        CustomerApp->>Ledger: recordTransaction(Amount: ₹500)
        Ledger->>Ledger: DEBIT: Customer Account (₹500)
        Ledger->>Ledger: CREDIT: Platform Account (₹500)
    end

    %% Delivery Flow
    Note over CustomerApp, Driver: ... Order is Prepared and Picked Up ...
    Driver->>CustomerApp: Marks Order as Delivered
    
    %% Payout Ledger Entries
    rect rgb(220, 240, 200)
        Note right of Ledger: [2] Payout Distribution
        CustomerApp->>Ledger: recordTransaction(Amount: ₹400)
        Note over CustomerApp, Ledger: Restaurant Payout (80% of ₹500)
        Ledger->>Ledger: DEBIT: Platform Account (₹400)
        Ledger->>Ledger: CREDIT: Restaurant Account (₹400)
        
        CustomerApp->>Ledger: recordTransaction(Amount: ₹50)
        Note over CustomerApp, Ledger: Driver Payout (Flat Fee)
        Ledger->>Ledger: DEBIT: Platform Account (₹50)
        Ledger->>Ledger: CREDIT: Driver Account (₹50)
    end
    
    %% Final State
    Note over Ledger, Platform: Final Balances:<br/>Platform Keeps: ₹50 (Commission)<br/>Restaurant Has: ₹400<br/>Driver Has: ₹50
```
