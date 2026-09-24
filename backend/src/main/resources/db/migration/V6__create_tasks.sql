CREATE TABLE devpulse.tasks (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL REFERENCES devpulse.projects (id),
    title VARCHAR(200) NOT NULL,
    description VARCHAR(5000),
    status VARCHAR(20) NOT NULL DEFAULT 'TODO',
    priority VARCHAR(10) NOT NULL DEFAULT 'MEDIUM',
    assignee_id UUID REFERENCES devpulse.users (id),
    created_by UUID NOT NULL REFERENCES devpulse.users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT tasks_title_not_blank CHECK (btrim(title) <> ''),
    CONSTRAINT tasks_status_valid CHECK (status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE')),
    CONSTRAINT tasks_priority_valid CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX tasks_project_id_created_at_id ON devpulse.tasks (project_id, created_at DESC, id);
CREATE INDEX tasks_assignee_id_project_id ON devpulse.tasks (assignee_id, project_id) WHERE assignee_id IS NOT NULL;

GRANT SELECT, INSERT ON TABLE devpulse.tasks TO devpulse_app;
GRANT UPDATE (title, description, status, priority, assignee_id, updated_at) ON devpulse.tasks TO devpulse_app;
