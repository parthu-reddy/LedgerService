package com.fooddelivery.ledger.reconciliation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/** Test fixture: the accounting calendar as deployed (Asia/Kolkata), with the clock fixed. */
final class AccountingCalendarFixture {

    private AccountingCalendarFixture() {}

    /** 2026-09-25T20:45Z, which is 02:15 IST on the 26th: after the nightly run's 02:00 start. */
    static final Instant NOW = Instant.parse("2026-09-25T20:45:00Z");

    static final AccountingCalendar KOLKATA = new AccountingCalendar(Clock.fixed(NOW, ZoneOffset.UTC), "Asia/Kolkata");
}
