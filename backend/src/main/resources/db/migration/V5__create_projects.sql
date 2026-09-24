CREATE TABLE devpulse.projects (
    id UUID PRIMARY KEY,
    team_id UUID NOT NULL REFERENCES devpulse.teams (id),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(2000),
    created_by UUID NOT NULL REFERENCES devpulse.users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT projects_name_not_blank CHECK (btrim(name) <> '')
);

CREATE INDEX projects_team_id_created_at_id
    ON devpulse.projects (team_id, created_at DESC, id);

GRANT SELECT, INSERT ON TABLE devpulse.projects TO devpulse_app;
