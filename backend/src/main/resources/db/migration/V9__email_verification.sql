ALTER TABLE devpulse.users
    ADD COLUMN email_verified_at TIMESTAMPTZ,
    ADD COLUMN email_verification_required BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing accounts retain access; only subsequent accounts require verification.
ALTER TABLE devpulse.users ALTER COLUMN email_verification_required SET DEFAULT TRUE;
GRANT UPDATE (email_verified_at) ON devpulse.users TO devpulse_app;

CREATE TABLE devpulse.email_verifications (
    user_id UUID PRIMARY KEY REFERENCES devpulse.users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    last_sent_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON devpulse.email_verifications TO devpulse_app;
