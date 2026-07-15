-- Add status and subscription_plan columns to gym_owner table
ALTER TABLE gym_owner ADD COLUMN status VARCHAR(50) DEFAULT 'active';
ALTER TABLE gym_owner ADD COLUMN subscription_plan VARCHAR(50) DEFAULT 'BASIC';

-- Create gym_features table to store active features for each gym
CREATE TABLE gym_features (
    gym_id UUID NOT NULL,
    feature_key VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by VARCHAR(100),
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (gym_id, feature_key),
    FOREIGN KEY (gym_id) REFERENCES gym_owner(id) ON DELETE CASCADE
);

-- Create feature_audit_logs table to track modifications
CREATE TABLE feature_audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id UUID,
    gym_id UUID NOT NULL,
    feature_key VARCHAR(100) NOT NULL,
    old_value BOOLEAN,
    new_value BOOLEAN NOT NULL,
    timestamp TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (gym_id) REFERENCES gym_owner(id) ON DELETE CASCADE
);
