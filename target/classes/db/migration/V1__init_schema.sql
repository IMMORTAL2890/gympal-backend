-- Enable UUID extension if not already available
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Define Enums
CREATE TYPE access_action AS ENUM ('enabled', 'disabled');
CREATE TYPE access_status AS ENUM ('allowed', 'blocked');
CREATE TYPE attendance_status AS ENUM ('present', 'absent');
CREATE TYPE device_sync_status AS ENUM ('success', 'failed', 'never');
CREATE TYPE membership_status AS ENUM ('active', 'expired', 'expiring_soon');
CREATE TYPE notif_status AS ENUM ('sent', 'failed', 'pending');
CREATE TYPE payment_mode AS ENUM ('cash', 'upi', 'card', 'other');
CREATE TYPE punch_source AS ENUM ('biometric', 'manual');
CREATE TYPE punch_type AS ENUM ('in', 'out', 'unknown');

-- Create Tables

-- gym_owner table
CREATE TABLE gym_owner (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_user_id UUID UNIQUE NOT NULL,
    gym_name TEXT NOT NULL,
    owner_name TEXT NOT NULL,
    mobile_number TEXT NOT NULL,
    auto_reminder_enabled BOOLEAN DEFAULT TRUE,
    reminder_days_before INT DEFAULT 3,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- platform_admins table
CREATE TABLE platform_admins (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auth_user_id UUID UNIQUE NOT NULL,
    full_name TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- trusted_ips table
CREATE TABLE trusted_ips (
    id BIGSERIAL PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES gym_owner(id) ON DELETE CASCADE,
    ip_address INET NOT NULL,
    label TEXT NOT NULL,
    last_seen_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- plans table
CREATE TABLE plans (
    id BIGSERIAL PRIMARY KEY,
    plan_name TEXT NOT NULL,
    duration_days INT NOT NULL,
    price NUMERIC(10,2) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    gym_owner_id UUID NOT NULL REFERENCES gym_owner(id) ON DELETE CASCADE
);

-- members table
CREATE TABLE members (
    id BIGSERIAL PRIMARY KEY,
    full_name TEXT NOT NULL,
    mobile_number TEXT NOT NULL,
    email TEXT,
    date_of_birth DATE,
    address TEXT,
    photo_url TEXT,
    joined_date DATE NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    biometric_uid TEXT,
    access_status access_status DEFAULT 'allowed',
    block_reason TEXT,
    blocked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    gym_owner_id UUID NOT NULL REFERENCES gym_owner(id) ON DELETE CASCADE,
    CONSTRAINT unique_gym_biometric UNIQUE (gym_owner_id, biometric_uid)
);

-- memberships table
CREATE TABLE memberships (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    plan_id BIGINT REFERENCES plans(id) ON DELETE SET NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    total_fee NUMERIC(10,2) NOT NULL,
    discount_amount NUMERIC(10,2) DEFAULT 0,
    discount_note TEXT,
    net_payable NUMERIC(10,2) GENERATED ALWAYS AS (total_fee - discount_amount) STORED,
    amount_paid NUMERIC(10,2) DEFAULT 0,
    due_amount NUMERIC(10,2) GENERATED ALWAYS AS (total_fee - discount_amount - amount_paid) STORED,
    payment_mode payment_mode,
    payment_status TEXT NOT NULL,
    status membership_status NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    gym_owner_id UUID NOT NULL REFERENCES gym_owner(id) ON DELETE CASCADE
);

-- payment_transactions table
CREATE TABLE payment_transactions (
    id BIGSERIAL PRIMARY KEY,
    membership_id BIGINT NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    amount NUMERIC(10,2) NOT NULL,
    payment_mode TEXT NOT NULL,
    payment_date DATE NOT NULL,
    note TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    gym_owner_id UUID NOT NULL REFERENCES gym_owner(id) ON DELETE CASCADE
);

-- biometric_devices table
CREATE TABLE biometric_devices (
    id BIGSERIAL PRIMARY KEY,
    device_name TEXT NOT NULL,
    device_ip TEXT NOT NULL,
    device_port INT DEFAULT 4370,
    device_password TEXT,
    device_model TEXT,
    firmware_version TEXT,
    device_serial TEXT,
    location TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    door_control_enabled BOOLEAN DEFAULT FALSE,
    last_sync_time TIMESTAMPTZ,
    last_sync_status device_sync_status DEFAULT 'never',
    last_sync_count INT DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    gym_owner_id UUID NOT NULL REFERENCES gym_owner(id) ON DELETE CASCADE
);

-- attendance_logs table
CREATE TABLE attendance_logs (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    biometric_uid TEXT,
    device_id BIGINT REFERENCES biometric_devices(id) ON DELETE SET NULL,
    punch_time TIMESTAMPTZ NOT NULL,
    punch_type punch_type DEFAULT 'unknown',
    verify_mode INT DEFAULT 0,
    source punch_source DEFAULT 'biometric',
    is_duplicate BOOLEAN DEFAULT FALSE,
    note TEXT,
    synced_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    gym_owner_id UUID NOT NULL REFERENCES gym_owner(id) ON DELETE CASCADE
);

-- attendance_summary table
CREATE TABLE attendance_summary (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    attendance_date DATE NOT NULL,
    first_in TIME,
    last_out TIME,
    total_punches INT DEFAULT 0,
    status attendance_status DEFAULT 'present',
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    gym_owner_id UUID NOT NULL REFERENCES gym_owner(id) ON DELETE CASCADE,
    CONSTRAINT unique_member_date UNIQUE (member_id, attendance_date)
);

-- access_control_logs table
CREATE TABLE access_control_logs (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    device_id BIGINT REFERENCES biometric_devices(id) ON DELETE SET NULL,
    action access_action NOT NULL,
    reason TEXT,
    performed_by TEXT DEFAULT 'owner',
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    gym_owner_id UUID NOT NULL REFERENCES gym_owner(id) ON DELETE CASCADE
);

-- notification_logs table
CREATE TABLE notification_logs (
    id BIGSERIAL PRIMARY KEY,
    membership_id BIGINT NOT NULL REFERENCES memberships(id) ON DELETE CASCADE,
    member_name TEXT NOT NULL,
    mobile_number TEXT NOT NULL,
    message TEXT NOT NULL,
    status notif_status NOT NULL,
    sent_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    gym_owner_id UUID NOT NULL REFERENCES gym_owner(id) ON DELETE CASCADE
);
