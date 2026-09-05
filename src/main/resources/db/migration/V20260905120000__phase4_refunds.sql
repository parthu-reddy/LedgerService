ALTER TABLE ledger_accounts ALTER COLUMN balance TYPE NUMERIC(14,2);
ALTER TABLE ledger_accounts ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'INR';
ALTER TABLE ledger_accounts ADD COLUMN kind VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE ledger_accounts ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT NOW();
ALTER TABLE ledger_accounts DROP CONSTRAINT unique_owner;
ALTER TABLE ledger_accounts ADD CONSTRAINT unique_owner UNIQUE (owner_type, owner_id);

ALTER TABLE ledger_entries ADD COLUMN reference_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000'::uuid;
ALTER TABLE ledger_entries ALTER COLUMN amount TYPE NUMERIC(14,2);
ALTER TABLE ledger_entries ALTER COLUMN direction TYPE VARCHAR(6);
ALTER TABLE ledger_entries ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE ledger_entries ADD COLUMN producer VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE ledger_entries ADD COLUMN description VARCHAR(255);
ALTER TABLE ledger_entries ADD COLUMN authorized_by VARCHAR(64);
ALTER TABLE ledger_entries DROP CONSTRAINT unique_transaction_direction;
ALTER TABLE ledger_entries ADD CONSTRAINT unique_transaction_direction UNIQUE (transaction_id, account_id, direction);

DROP TABLE failed_deferred_updates;

CREATE TABLE ledger_rejections (
    id UUID PRIMARY KEY,
    event_id VARCHAR(255),
    producer VARCHAR(64),
    payload JSONB NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ,
    resolved_by VARCHAR(64),
    resolution_note VARCHAR(1000)
);

CREATE INDEX idx_ledger_entries_account_created ON ledger_entries(account_id, created_at);
CREATE INDEX idx_ledger_rejections_resolved ON ledger_rejections(resolved_at);
