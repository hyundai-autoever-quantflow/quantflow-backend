-- Durable credentials are AES-256-GCM ciphertext. credential_ref contains only the UUID reference.
CREATE TABLE broker_credentials (
    credential_id VARCHAR(36) PRIMARY KEY,
    encrypted_payload TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

-- NULL for pre-existing links. A verification ticket can create exactly one connection.
ALTER TABLE broker_connections ADD COLUMN verification_ticket_hash VARCHAR(64);
ALTER TABLE broker_connections ADD CONSTRAINT uk_broker_connections_ticket UNIQUE (verification_ticket_hash);

-- DB constraint closes the concurrent duplicate-account race, including across members.
-- Existing active duplicates must be resolved before this migration can succeed.
CREATE UNIQUE INDEX uk_broker_connections_active_account
    ON broker_connections (broker, account_fingerprint)
    WHERE status NOT IN ('DISCONNECTED', 'FAILED');
