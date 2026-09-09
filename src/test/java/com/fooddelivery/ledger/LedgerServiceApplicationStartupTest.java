package com.fooddelivery.ledger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The service's Spring wiring loads: every bean this application defines can be constructed.
 *
 * <p><b>This does not validate the schema, and cannot.</b> Testcontainers are excluded by project
 * rule (see {@code CodingPracticesAcrossAllServices/05_DataLayer/jpa-and-flyway.md}), and H2 cannot
 * execute {@code V1__init_ledger.sql} even in PostgreSQL mode — measured, not assumed:
 *
 * <pre>
 *   CREATE TABLE ledger_accounts ...   Unknown data type: "TIMESTAMPTZ"
 *   CREATE UNIQUE INDEX uq_payout_line_entry_active ... WHERE active = true   Syntax error
 * </pre>
 *
 * <p>So there is no in-process database that can run the shipped Postgres schema and then be checked
 * with {@code ddl-auto: validate}. {@link LedgerSchemaConsistencyTest} covers that ground instead by
 * parsing the migration and comparing it against the entity mappings, with no database at all — and
 * it catches the class of defect that matters here, a column the entities disagree with, which stops
 * the service booting under the production {@code ddl-auto: validate}.
 *
 * <p>This class previously forced {@code ddl-auto=validate} onto an empty H2 with Flyway disabled,
 * so it could only ever fail, and it did.
 */
@SpringBootTest
@ActiveProfiles("test")
public class LedgerServiceApplicationStartupTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertNotNull(context, "every bean the ledger service defines must be constructible");
    }

    /** The beans that move money must be present, not merely a context that started. */
    @Test
    void theBeansThatMoveMoneyArePresent() {
        for (Class<?> required : new Class<?>[]{
                com.fooddelivery.ledger.service.DoubleEntryLedgerService.class,
                com.fooddelivery.ledger.service.PayoutService.class,
                com.fooddelivery.ledger.service.CashService.class,
                com.fooddelivery.ledger.service.OwnerNameResolver.class,
                com.fooddelivery.ledger.reconciliation.ReconciliationService.class,
                com.fooddelivery.ledger.listener.LedgerEventListener.class}) {
            assertNotNull(context.getBean(required), required.getSimpleName() + " is not in the context");
        }
    }

    /** Every endpoint the money screens and the SERVICE clients call must be mapped. */
    @Test
    void everyMoneyEndpointIsMapped() {
        org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping mapping =
                context.getBean("requestMappingHandlerMapping",
                        org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class);
        String mapped = mapping.getHandlerMethods().keySet().toString();

        for (String path : new String[]{
                "/api/v1/internal/admin/payouts",
                "/api/v1/internal/admin/payouts/pending",
                "/api/v1/internal/admin/cash",
                "/api/v1/internal/ledger/payouts",
                "/api/v1/internal/ledger/cash/drivers/{driverId}/summary",
                "/api/v1/ledger/payouts",
                "/api/v1/ledger/cash/drivers/{driverId}",
                "/api/v1/ledger/statements"}) {
            assertTrue(mapped.contains(path), "no handler is mapped at " + path + "\nmapped: " + mapped);
        }
    }
}
