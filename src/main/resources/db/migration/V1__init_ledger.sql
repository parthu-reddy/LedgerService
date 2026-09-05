CREATE TABLE ledger_accounts (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    owner_type VARCHAR(50) NOT NULL,
    kind VARCHAR(50) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    balance NUMERIC(14,2) NOT NULL DEFAULT 0.00,
    lock_version INT NOT NULL DEFAULT 0,
    CONSTRAINT unique_owner UNIQUE (owner_id, owner_type)
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL,
    account_id UUID NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_id) REFERENCES ledger_accounts(id),
    category VARCHAR(50) NOT NULL,
    CONSTRAINT unique_transaction_direction UNIQUE (transaction_id, direction),
    reference_id UUID NOT NULL,
    producer VARCHAR(100) NOT NULL,
    authorized_by VARCHAR(100),
    description VARCHAR(255)
);

CREATE TABLE ledger_rejections (
    id UUID PRIMARY KEY,
    producer VARCHAR(100) NOT NULL,
    reference_id UUID NOT NULL,
    payload TEXT NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ledger_entries_tx ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_entries_reference_id ON ledger_entries(reference_id);
CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_created_at ON ledger_entries(created_at);

CREATE TABLE payouts (
    id UUID PRIMARY KEY,
    payee_type VARCHAR(32) NOT NULL,
    payee_id UUID NOT NULL,
    payee_display_name VARCHAR(255) NOT NULL,
    period_from TIMESTAMP NOT NULL,
    period_to TIMESTAMP NOT NULL,
    amount NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(16) NOT NULL,
    beneficiary_snapshot VARCHAR(2000) NOT NULL,
    bank_reference VARCHAR(128),
    failure_reason VARCHAR(1000),
    created_by UUID NOT NULL,
    approved_by UUID,
    paid_by UUID,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    ledger_transaction_id UUID NOT NULL,
    settled_transaction_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    approved_at TIMESTAMP,
    paid_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE payout_lines (
    id UUID PRIMARY KEY,
    payout_id UUID NOT NULL REFERENCES payouts(id),
    ledger_entry_id UUID NOT NULL REFERENCES ledger_entries(id),
    reference_id UUID NOT NULL,
    category VARCHAR(40) NOT NULL,
    direction VARCHAR(6) NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    entry_created_at TIMESTAMP NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX uq_payout_line_entry_active ON payout_lines(ledger_entry_id) WHERE active = true;

CREATE TABLE cash_remittances (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL,
    amount NUMERIC(14,2) NOT NULL,
    reference VARCHAR(128),
    recorded_by UUID NOT NULL,
    ledger_transaction_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reconciliation_runs (
    id UUID PRIMARY KEY,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    summary JSONB
);

CREATE TABLE reconciliation_breaks (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES reconciliation_runs(id),
    kind VARCHAR(50) NOT NULL,
    subject_type VARCHAR(50) NOT NULL,
    subject_id UUID NOT NULL,
    expected NUMERIC(14,2) NOT NULL,
    actual NUMERIC(14,2) NOT NULL,
    detail JSONB,
    resolved_at TIMESTAMP,
    resolved_by UUID,
    note VARCHAR(1000)
);

CREATE INDEX idx_reconciliation_breaks_run_id ON reconciliation_breaks(run_id);
CREATE INDEX idx_reconciliation_breaks_subject ON reconciliation_breaks(subject_type, subject_id);
