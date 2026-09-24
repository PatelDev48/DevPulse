ALTER TABLE devpulse.projects
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    ADD COLUMN archived_at TIMESTAMPTZ;

GRANT UPDATE (name, description, updated_at, archived_at) ON devpulse.projects TO devpulse_app;