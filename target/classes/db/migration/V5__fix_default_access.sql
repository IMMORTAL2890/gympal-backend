-- Alter default access_status for new members
ALTER TABLE members ALTER COLUMN access_status SET DEFAULT 'blocked';

-- Fix existing members who were improperly defaulted to 'allowed' despite having no active plans
UPDATE members
SET access_status = 'blocked',
    block_reason = 'System fix: No active membership plan found',
    blocked_at = CURRENT_TIMESTAMP
WHERE access_status = 'allowed'
  AND NOT EXISTS (
      SELECT 1 FROM memberships m 
      WHERE m.member_id = members.id 
      AND m.status IN ('active', 'expiring_soon')
  );
