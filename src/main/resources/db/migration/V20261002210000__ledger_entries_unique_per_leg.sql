-- A ledger transaction is a compound journal entry: the delivered-order distribution debits the
-- platform clearing account four times (food cost, delivery fee, two taxes) and credits tax payable
-- four times. UNIQUE(transaction_id, account_id, direction) allowed one debit and one credit per
-- account, so every such distribution was rejected on its second leg and no delivered order ever
-- posted its restaurant/rider earnings. The constraint's real job is a database backstop for
-- posting one transaction twice; uniqueness per leg keeps that and admits compound entries.
ALTER TABLE ledger_entries ADD COLUMN IF NOT EXISTS leg_index INT;

-- Existing transactions had at most one entry per account and direction; number them in posting order.
UPDATE ledger_entries e
SET leg_index = numbered.rn - 1
FROM (SELECT id, row_number() OVER (PARTITION BY transaction_id, direction ORDER BY created_at, id) AS rn
      FROM ledger_entries) numbered
WHERE numbered.id = e.id AND e.leg_index IS NULL;

ALTER TABLE ledger_entries ALTER COLUMN leg_index SET NOT NULL;
ALTER TABLE ledger_entries DROP CONSTRAINT IF EXISTS ledger_entries_transaction_id_account_id_direction_key;
ALTER TABLE ledger_entries
    ADD CONSTRAINT ledger_entries_transaction_leg_direction_key UNIQUE (transaction_id, leg_index, direction);
