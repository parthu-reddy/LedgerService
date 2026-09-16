DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM cash_remittances)
        OR EXISTS (SELECT 1 FROM ledger_accounts WHERE owner_type = 'CASH_RECEIVABLE')
        OR EXISTS (
            SELECT 1 FROM ledger_entries
            WHERE category IN ('CASH_COLLECTED', 'CASH_SHORTFALL', 'CASH_REMITTED')
        ) THEN
        RAISE EXCEPTION 'Prepaid-only migration blocked: unresolved cash ledger data exists';
    END IF;
END $$;

DROP TABLE cash_remittances;

ALTER TABLE ledger_accounts
    ADD CONSTRAINT chk_ledger_account_type_no_cash
    CHECK (owner_type <> 'CASH_RECEIVABLE');

ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_entry_category_no_cash
    CHECK (category NOT IN ('CASH_COLLECTED', 'CASH_SHORTFALL', 'CASH_REMITTED'));
