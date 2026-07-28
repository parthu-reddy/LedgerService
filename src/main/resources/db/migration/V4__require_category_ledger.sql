-- PostgreSQL compatible script to make the existing category column NOT NULL.
ALTER TABLE ledger_entries ALTER COLUMN category SET NOT NULL;
