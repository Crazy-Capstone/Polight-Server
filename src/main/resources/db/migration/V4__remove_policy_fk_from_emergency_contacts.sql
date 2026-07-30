DROP INDEX IF EXISTS idx_emergency_contacts_policy_id;

ALTER TABLE IF EXISTS emergency_contacts
  DROP CONSTRAINT IF EXISTS fk_emergency_contacts_policy,
  DROP CONSTRAINT IF EXISTS emergency_contacts_policy_id_fkey,
  DROP COLUMN IF EXISTS policy_id;
