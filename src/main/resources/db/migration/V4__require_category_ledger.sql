-- Oracle compatible script to make the existing category column NOT NULL.
-- In Oracle, if the column contains NULLs, this will fail. We assume data is clean or dev environment.
ALTER TABLE ledger_entries MODIFY category VARCHAR2(50) NOT NULL;
