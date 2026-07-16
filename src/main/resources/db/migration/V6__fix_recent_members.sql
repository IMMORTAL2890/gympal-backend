-- Fix any members who were created with 'allowed' during the timeframe before MemberService.java was fixed
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
