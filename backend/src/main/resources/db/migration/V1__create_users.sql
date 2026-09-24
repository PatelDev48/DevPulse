CREATE TABLE devpulse.users (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT users_name_not_blank CHECK (btrim(name) <> ''),
    CONSTRAINT users_email_normalized CHECK (
        email <> '' AND email = lower(btrim(email))
    ),
    CONSTRAINT users_email_unique UNIQUE (email)
);

GRANT USAGE ON SCHEMA devpulse TO devpulse_app;
GRANT SELECT, INSERT ON TABLE devpulse.users TO devpulse_app;