DROP TABLE IF EXISTS ledger_entries CASCADE;
DROP TABLE IF EXISTS ledger_accounts CASCADE;
DROP TABLE IF EXISTS failed_deferred_updates CASCADE;

CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    owner_type VARCHAR(40) NOT NULL,
    owner_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL,
    balance NUMERIC(14,2) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    lock_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(owner_type, owner_id)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    reference_id UUID NOT NULL,
    account_id UUID NOT NULL REFERENCES ledger_accounts,
    direction VARCHAR(6) NOT NULL,
    category VARCHAR(40) NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    producer VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    authorized_by VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(transaction_id, account_id, direction)
);

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

CREATE INDEX idx_entries_reference_id ON ledger_entries(reference_id);
CREATE INDEX idx_entries_account_created ON ledger_entries(account_id, created_at);
CREATE INDEX idx_entries_transaction_id ON ledger_entries(transaction_id);
CREATE INDEX idx_rejections_resolved_at ON ledger_rejections(resolved_at);
