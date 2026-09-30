-- Payouts existed before their lifecycle actions were made serialisable. Existing rows receive a
-- zero version so Hibernate can start validating the mapping immediately after this migration.
ALTER TABLE payouts
    ADD COLUMN lock_version INTEGER NOT NULL DEFAULT 0;

-- This is both the idempotency ledger and the operator audit trail for a payout transition. A
-- globally unique key rejects accidentally reusing one client-generated operation key for another
-- payout or action; the service compares the full request hash before acknowledging a replay.
CREATE TABLE payout_operations (
    id UUID PRIMARY KEY,
    payout_id UUID NOT NULL REFERENCES payouts(id),
    action VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    actor_id UUID NOT NULL,
    status_before VARCHAR(16) NOT NULL,
    status_after VARCHAR(16) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    bank_reference VARCHAR(128),
    failure_reason VARCHAR(1000),
    ledger_transaction_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_payout_operation_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT chk_payout_operation_action CHECK (action IN ('APPROVE', 'MARK_PAID', 'FAIL', 'CANCEL')),
    CONSTRAINT chk_payout_operation_outcome CHECK (outcome = 'APPLIED'),
    CONSTRAINT chk_payout_operation_status_change CHECK (status_before <> status_after),
    CONSTRAINT chk_payout_operation_status_after CHECK (
        (action = 'APPROVE' AND status_before = 'DRAFT' AND status_after = 'APPROVED') OR
        (action = 'MARK_PAID' AND status_before = 'APPROVED' AND status_after = 'PAID') OR
        (action = 'FAIL' AND status_before IN ('DRAFT', 'APPROVED') AND status_after = 'FAILED') OR
        (action = 'CANCEL' AND status_before = 'DRAFT' AND status_after = 'CANCELLED')
    ),
    CONSTRAINT chk_payout_operation_input CHECK (
        (action = 'MARK_PAID' AND bank_reference IS NOT NULL AND failure_reason IS NULL) OR
        (action = 'FAIL' AND bank_reference IS NULL AND failure_reason IS NOT NULL) OR
        (action IN ('APPROVE', 'CANCEL') AND bank_reference IS NULL AND failure_reason IS NULL)
    ),
    CONSTRAINT chk_payout_operation_ledger_transaction CHECK (
        (action = 'APPROVE' AND ledger_transaction_id IS NULL) OR
        (action IN ('MARK_PAID', 'FAIL', 'CANCEL') AND ledger_transaction_id IS NOT NULL)
    )
);

CREATE INDEX idx_payout_operations_payout_created ON payout_operations(payout_id, created_at);

-- Operations are audit evidence. Application mappings mark every field updatable=false, and this
-- trigger protects the same invariant against an accidental direct SQL update or delete.
CREATE OR REPLACE FUNCTION prevent_payout_operation_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'payout_operations is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_payout_operations_immutable
    BEFORE UPDATE OR DELETE ON payout_operations
    FOR EACH ROW EXECUTE FUNCTION prevent_payout_operation_mutation();
