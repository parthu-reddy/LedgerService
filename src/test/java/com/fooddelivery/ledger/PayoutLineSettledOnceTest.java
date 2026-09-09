package com.fooddelivery.ledger;

import com.fooddelivery.ledger.repository.ILedgerEntryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A ledger entry may be settled by one payout, once.
 *
 * <p>Two things enforce it, and neither is reachable from a mock-based service test: the JPQL that
 * selects unsettled entries, and the partial unique index behind it. {@code PayoutServiceTest} stubs
 * {@code findUnsettledEntries}, so deleting the exclusion from the query left all fifteen of its
 * cases green — found 2026-09-09 while performing Phase 5's break-test 1.
 *
 * <p>Checked by reading the query and the migration rather than by running them: Testcontainers are
 * excluded by project rule and H2 cannot execute this schema.
 */
class PayoutLineSettledOnceTest {

    private static String migration() {
        try {
            return Files.readString(Path.of("src/main/resources/db/migration/V1__init_ledger.sql"),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String unsettledEntriesQuery() {
        for (Method m : ILedgerEntryRepository.class.getDeclaredMethods()) {
            if (m.getName().equals("findUnsettledEntries")) {
                Query q = m.getAnnotation(Query.class);
                assertTrue(q != null, "findUnsettledEntries has no @Query to inspect");
                return q.value();
            }
        }
        throw new AssertionError("ILedgerEntryRepository.findUnsettledEntries no longer exists");
    }

    /** The query must exclude entries already carried by an active payout line. */
    @Test
    void theUnsettledQueryExcludesLinesAlreadyInAPayout() {
        String jpql = unsettledEntriesQuery().replaceAll("\\s+", " ");

        assertTrue(jpql.contains("NOT EXISTS"),
                "findUnsettledEntries must exclude entries already in a payout, was: " + jpql);
        assertTrue(jpql.contains("PayoutLine"),
                "the exclusion must be against PayoutLine, was: " + jpql);
        assertTrue(jpql.contains("active = true"),
                "only an ACTIVE payout line settles an entry; a failed or cancelled payout frees it again, was: " + jpql);
    }

    /**
     * The database backstop. Even if the query regressed, the index makes a double settlement an
     * error rather than a silent double payment.
     */
    @Test
    void theSchemaForbidsTwoActiveLinesForOneEntry() {
        String sql = migration().replaceAll("\\s+", " ");

        assertTrue(sql.contains("CREATE UNIQUE INDEX uq_payout_line_entry_active ON payout_lines(ledger_entry_id) WHERE active = true"),
                "the partial unique index on payout_lines(ledger_entry_id) WHERE active = true is gone; "
                + "nothing then stops one ledger entry being paid out twice");
    }

    /** A payout that fails or is cancelled must release its lines, or the money is stranded. */
    @Test
    void payoutLinesCarryAnActiveFlagToRelease() {
        String sql = migration().replaceAll("\\s+", " ");
        assertTrue(sql.contains("active BOOLEAN NOT NULL DEFAULT TRUE"),
                "payout_lines needs the active flag that fail() and cancel() clear");
    }
}
