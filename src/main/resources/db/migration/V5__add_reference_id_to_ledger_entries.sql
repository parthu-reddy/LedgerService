ALTER TABLE ledger_entries ADD COLUMN reference_id UUID;
CREATE INDEX idx_ledger_entries_reference_id ON ledger_entries(reference_id);
