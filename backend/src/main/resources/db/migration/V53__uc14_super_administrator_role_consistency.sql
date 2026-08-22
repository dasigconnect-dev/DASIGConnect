-- Keep the compatibility flag aligned with the UC-1.4 role enum.
-- Authorization uses the role value; the flag remains in JWTs for clients
-- introduced by UC-1.1 that still read the super_administrator claim.
UPDATE users
SET is_super_administrator = (role = 'super_administrator')
WHERE is_super_administrator IS DISTINCT FROM (role = 'super_administrator');

-- UC-1.1 created this index while super administrators were represented as
-- role=administrator plus a boolean. UC-1.4 uses a distinct enum role, so
-- rebuild the index against that source of truth.
DROP INDEX IF EXISTS idx_users_single_active_super_admin;
CREATE UNIQUE INDEX idx_users_single_active_super_admin
    ON users ((role))
    WHERE role = 'super_administrator'
      AND account_state = 'active';
