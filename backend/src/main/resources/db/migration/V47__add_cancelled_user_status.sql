ALTER TABLE users DROP CONSTRAINT IF EXISTS chk_users_account_state;
ALTER TABLE users ADD CONSTRAINT chk_users_account_state CHECK (
    account_state IN ('pending', 'pending_email_undelivered', 'active', 'expired', 'inactive', 'cancelled')
);

UPDATE users SET account_state = 'cancelled' WHERE account_state = 'inactive' AND (password_hash IS NULL OR password_hash = '');

