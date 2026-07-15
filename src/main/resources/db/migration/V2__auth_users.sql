-- Create app_users table to back Spring Security authentication
CREATE TABLE app_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email TEXT UNIQUE NOT NULL,
    password_hash TEXT, -- NULL for Google OAuth-only accounts
    role TEXT NOT NULL DEFAULT 'OWNER', -- 'OWNER' or 'ADMIN'
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Link gym_owner and platform_admins to app_users
ALTER TABLE gym_owner
ADD CONSTRAINT fk_gym_owner_auth_user FOREIGN KEY (auth_user_id) REFERENCES app_users(id) ON DELETE CASCADE;

ALTER TABLE platform_admins
ADD CONSTRAINT fk_platform_admins_auth_user FOREIGN KEY (auth_user_id) REFERENCES app_users(id) ON DELETE CASCADE;
