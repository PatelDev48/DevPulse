CREATE TABLE devpulse.teams (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT teams_name_not_blank CHECK (btrim(name) <> '')
);

CREATE TABLE devpulse.team_memberships (
    team_id UUID NOT NULL REFERENCES devpulse.teams (id),
    user_id UUID NOT NULL REFERENCES devpulse.users (id),
    role VARCHAR(10) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (team_id, user_id),
    CONSTRAINT team_memberships_role_valid CHECK (role IN ('OWNER', 'MEMBER'))
);

CREATE UNIQUE INDEX team_memberships_one_owner_per_team
    ON devpulse.team_memberships (team_id) WHERE role = 'OWNER';

CREATE INDEX team_memberships_user_id_team_id
    ON devpulse.team_memberships (user_id, team_id);

GRANT SELECT, INSERT ON TABLE devpulse.teams, devpulse.team_memberships TO devpulse_app;