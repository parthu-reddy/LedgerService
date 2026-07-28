ALTER TABLE ledger_entries ADD COLUMN category VARCHAR(50);

ALTER TABLE ledger_entries ADD CONSTRAINT unique_transaction_direction UNIQUE (transaction_id, direction);
