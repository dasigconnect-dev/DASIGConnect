-- Moderators become network-wide, like admin: no owning institution.
UPDATE users SET institution_id = NULL WHERE role = 'moderator';
