# Ledger Microservice Extraction Plan

Provide a detailed architectural plan for extracting the Double-Entry Ledger functionality out of the `CustomerApplication` and into a dedicated `LedgerService`. This will improve scalability, security, and separate core financial accounting from customer order flows.

> [!NOTE]
> This is a structural roadmap. The core idea is to shift from synchronous in-memory database writes (tight coupling) to an event-driven architecture using the existing Outbox pattern/message broker.

## User Review Required

> [!IMPORTANT]
> **Event-Driven vs Synchronous:** Moving to a separate service means we should ideally rely on asynchronous events (e.g., listening to `ORDER_DELIVERED` via Kafka) to record ledger entries, rather than synchronous REST API calls. This guarantees high availability but means the ledger is *eventually consistent*. 
> Is eventual consistency acceptable for ledger updates in this system?

## Open Questions

> [!WARNING]
> 1. **Message Broker**: The system already uses an Outbox pattern (`OutboxEventEntity`). Is Kafka or RabbitMQ currently set up to route these events between microservices?
> 2. **Historical Data**: Are there millions of rows in `ledger_entries` that require a specialized zero-downtime ETL migration script, or is a standard SQL dump/restore sufficient for the current scale?

---

## Proposed Changes

### 1. Create `LedgerService` (New Microservice)

A brand new Spring Boot application (`LedgerService`) will be created.

#### [NEW] `LedgerService/src/main/java/.../entity/LedgerEntry.java`
#### [NEW] `LedgerService/src/main/java/.../entity/LedgerAccount.java`
*   Move the JPA entities from `CustomerApplication` to the new service.

#### [NEW] `LedgerService/src/main/java/.../service/DoubleEntryLedgerService.java`
*   Move the core financial logic here.

#### [NEW] `LedgerService/src/main/java/.../listener/OrderEventListener.java`
*   **Crucial Addition**: A message broker listener (e.g., Kafka `@KafkaListener`) that listens to existing domain events like `ORDER_PAID`, `ORDER_DELIVERED`, and `ORDER_CANCELLED`.
*   The logic currently hardcoded in `PickedUpState.java` (calculating the 80% restaurant payout and Rs 50 driver payout) will be moved into this listener. 

#### [NEW] `LedgerService/src/main/java/.../controller/LedgerController.java`
*   Expose read-only REST APIs for other services to query balances (e.g., `GET /api/v1/ledger/accounts/{accountId}/balance`).

---

### 2. Refactor `CustomerApplication`

We must strip out the financial logic so `CustomerApplication` only focuses on order state management.

#### [DELETE] `CustomerApplication/.../entity/LedgerEntry.java`
#### [DELETE] `CustomerApplication/.../entity/LedgerAccount.java`
#### [DELETE] `CustomerApplication/.../service/DoubleEntryLedgerService.java`
*   Remove these files entirely.

#### [MODIFY] `CustomerApplication/.../service/state/impl/PickedUpState.java`
*   Remove lines 25-42 where `restPayout` and `driverPayout` are calculated and synchronously recorded.
*   The service already calls `sendNotification` and saves the order status. The Outbox will handle broadcasting the `ORDER_DELIVERED` event, which the new `LedgerService` will pick up.

#### [MODIFY] `CustomerApplication/.../service/state/impl/TerminalState.java`
*   Remove lines 23-31 where the initial customer payment is recorded into the ledger synchronously.
*   This will now be handled by the `LedgerService` listening for the `ORDER_PAID` event.

#### [MODIFY] `CustomerApplication/.../service/state/OrderActionService.java`
*   Remove the `recordLedgerTransaction` wrapper method.

---

### 3. Database Migration Strategy

The data must physically move from the `CustomerDB` to a new `LedgerDB`.

1. **Schema Creation:**
   Create a new PostgreSQL/Oracle database `ledger_db` and run Flyway/Liquibase migrations to create the `ledger_accounts` and `ledger_entries` tables.
2. **Data Transfer:**
   Create a SQL script to `INSERT INTO ledger_db.ledger_accounts SELECT * FROM customer_db.ledger_accounts`.
3. **Cleanup:**
   Drop the `ledger_accounts` and `ledger_entries` tables from `CustomerDB`.

---

### 4. Infrastructure & Deployment Updates

#### [MODIFY] `ApiGateway/.../GatewayConfig.java` (or application.yml)
*   Add routing rules for `/api/v1/ledger/**` to route to `LedgerService`.

#### [MODIFY] `Deployment/docker-compose.yml` (and/or Kubernetes manifests)
*   Add the `ledger-service` container definition.
*   Add the `ledger-db` container or database schema initialization block.

---

## Verification Plan

### Automated Tests
- Run `CustomerApplication` unit and integration tests to ensure order state transitions still succeed without the ledger dependencies.
- Write new Consumer Contract Tests (e.g., Spring Cloud Contract) in `LedgerService` to prove it correctly processes `ORDER_PAID` and `ORDER_DELIVERED` JSON payloads.

### Manual Verification
1. **End-to-End Flow:** Place an order, complete payment, assign a driver, and mark as delivered.
2. **Validation:** Check the `LedgerDB` directly to verify that:
   - The platform account was credited the full amount.
   - The restaurant account was credited 80%.
   - The driver account was credited Rs 50.
3. **No Phantom Money:** Ensure `CustomerDB` no longer contains ledger tables and the app throws no SQL exceptions during checkout.
