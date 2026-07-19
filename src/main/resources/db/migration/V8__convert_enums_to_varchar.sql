-- Convert native PostgreSQL enum columns to standard varchar
ALTER TABLE members ALTER COLUMN access_status TYPE varchar USING access_status::text;
ALTER TABLE access_control_logs ALTER COLUMN action TYPE varchar USING action::text;
ALTER TABLE notification_logs ALTER COLUMN status TYPE varchar USING status::text;
ALTER TABLE attendance_logs ALTER COLUMN punch_type TYPE varchar USING punch_type::text;
ALTER TABLE attendance_logs ALTER COLUMN source TYPE varchar USING source::text;
ALTER TABLE attendance_summary ALTER COLUMN status TYPE varchar USING status::text;
ALTER TABLE biometric_devices ALTER COLUMN last_sync_status TYPE varchar USING last_sync_status::text;
ALTER TABLE memberships ALTER COLUMN payment_mode TYPE varchar USING payment_mode::text;
ALTER TABLE memberships ALTER COLUMN status TYPE varchar USING status::text;
