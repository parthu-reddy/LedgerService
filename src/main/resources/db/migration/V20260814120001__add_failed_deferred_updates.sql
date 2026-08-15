CREATE TABLE failed_deferred_updates (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    amount DECIMAL NOT NULL,
    created_at TIMESTAMP NOT NULL,
    error_reason VARCHAR(1000),
    resolved BOOLEAN NOT NULL DEFAULT FALSE
);
