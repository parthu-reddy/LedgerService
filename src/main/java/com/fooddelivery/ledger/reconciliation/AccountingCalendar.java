package com.fooddelivery.ledger.reconciliation;

import com.fooddelivery.common.time.BusinessCalendar;
import com.fooddelivery.common.time.TimeWindow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The calendar the books close on: which day a ledger entry, a captured payment and a paid order
 * belong to when they are reconciled against each other.
 *
 * <p>Reconciliation used to ask each service for {@code CAST(created_at AS date) = :date}, which each
 * database session evaluated in its own zone (pgjdbc takes it from the JVM). The day only matched
 * across services while every JVM happened to run in UTC. Now the ledger turns the date into one
 * {@link TimeWindow} here and every service sums the same {@code [from, to)} instants.
 *
 * <p>The zone is {@code platform.accounting-zone} ({@code PLATFORM_ACCOUNTING_ZONE}). It has no default:
 * a ledger that doesn't know which zone its books close in must not start.
 * RandomDocuments/TimezoneCorrectness_2026-09-25.
 */
@Component
public class AccountingCalendar {

    private final Clock clock;
    private final ZoneId zone;

    public AccountingCalendar(Clock clock, @Value("${platform.accounting-zone}") String zone) {
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public ZoneId zone() {
        return zone;
    }

    /** The accounting date it is now. */
    public LocalDate today() {
        return BusinessCalendar.today(clock, zone);
    }

    /** {@code date} as the instants it covers in the accounting zone. */
    public TimeWindow day(LocalDate date) {
        return BusinessCalendar.day(date, zone);
    }
}
