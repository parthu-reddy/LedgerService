CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    owner_type VARCHAR(50) NOT NULL,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    lock_version INT NOT NULL DEFAULT 0,
    CONSTRAINT unique_owner UNIQUE (owner_id, owner_type)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    account_id UUID NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_id) REFERENCES ledger_accounts(id),
    category VARCHAR(50) NOT NULL,
    CONSTRAINT unique_transaction_direction UNIQUE (transaction_id, direction),
    reference_id UUID
);

CREATE TABLE failed_deferred_updates (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    amount DECIMAL NOT NULL,
    created_at TIMESTAMP NOT NULL,
    error_reason VARCHAR(1000),
    resolved BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_ledger_entries_tx ON ledger_entries(transaction_id);

CREATE INDEX idx_ledger_entries_reference_id ON ledger_entries(reference_id);

CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);

CREATE INDEX idx_ledger_entries_created_at ON ledger_entries(created_at);

