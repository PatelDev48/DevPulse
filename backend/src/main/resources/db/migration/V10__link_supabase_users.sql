ALTER TABLE devpulse.users
    ADD COLUMN supabase_user_id UUID;

CREATE UNIQUE INDEX users_supabase_user_id_uq
    ON devpulse.users (supabase_user_id)
    WHERE supabase_user_id IS NOT NULL;
