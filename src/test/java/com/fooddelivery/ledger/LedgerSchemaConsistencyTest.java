package com.fooddelivery.ledger;

import com.fooddelivery.common.test.SchemaConsistency;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The ledger's entities must agree with V1__init_ledger.sql, because production runs
 * {@code ddl-auto: validate} and a disagreement stops the service booting.
 *
 * <p>See {@link SchemaConsistency} for why this is static rather than a real database: Testcontainers
 * are excluded by project rule and H2 cannot execute this migration at all.
 */
public class LedgerSchemaConsistencyTest {

    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    @Test
    void entitiesAgreeWithTheMigration() {
        List<String> problems = SchemaConsistency.mismatches(MIGRATIONS, "com.fooddelivery.ledger", Set.of());
        if (!problems.isEmpty()) {
            fail("Entities and the ledger migration disagree; ddl-auto=validate would refuse to start:\n  "
                    + String.join("\n  ", problems));
        }
    }

    @Test
    void theMigrationIsActuallyParsed() {
        // A parser that silently read nothing would make the check above vacuously green.
        Map<String, Map<String, String>> schema = SchemaConsistency.parseMigrations(MIGRATIONS);
        assertTrue(schema.containsKey("ledger_entries") && schema.get("ledger_entries").containsKey("created_at"),
                "the parser found no ledger_entries.created_at, so it is not reading the migration: " + schema.keySet());
        assertTrue(schema.size() >= 8, "expected at least 8 tables, parsed: " + schema.keySet());
    }

    @Test
    void everyLedgerEntityWasScanned() {
        assertTrue(SchemaConsistency.entities("com.fooddelivery.ledger").size() >= 7,
                "entity scan found " + SchemaConsistency.entities("com.fooddelivery.ledger").size());
    }
}
