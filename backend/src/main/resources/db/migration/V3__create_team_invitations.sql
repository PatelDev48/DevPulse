CREATE TABLE devpulse.team_invitations (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES devpulse.teams (id),
    created_by UUID NOT NULL REFERENCES devpulse.users (id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    accepted_by UUID REFERENCES devpulse.users (id),
    revoked_at TIMESTAMPTZ,
    CONSTRAINT team_invitations_hash_valid CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT team_invitations_expiry_valid CHECK (expires_at > created_at),
    CONSTRAINT team_invitations_acceptance_valid CHECK (
        (accepted_at IS NULL) = (accepted_by IS NULL)
    ),
    CONSTRAINT team_invitations_terminal_state_valid CHECK (
        accepted_at IS NULL OR revoked_at IS NULL
    )
);

CREATE INDEX team_invitations_team_id ON devpulse.team_invitations (team_id);

GRANT SELECT, INSERT ON TABLE devpulse.team_invitations TO devpulse_app;
GRANT UPDATE (accepted_at, accepted_by, revoked_at) ON devpulse.team_invitations TO devpulse_app;