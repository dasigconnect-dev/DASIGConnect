-- ╔══════════════════════════════════════════════════════════════════════════════════╗
-- ║  DEV-ONLY SEED — DO NOT MERGE TO main / DO NOT APPLY TO PRODUCTION.                ║
-- ║  This migration creates known-password user accounts. Flyway runs on every         ║
-- ║  environment, so if this file reaches prod it provisions live accounts with a       ║
-- ║  committed BCrypt hash. REMOVE this file (or gate it to a dev-only Flyway           ║
-- ║  location) before any merge toward main. Tracked in CAPSTONE2_PHASE2_PLAN.md §5.    ║
-- ╚══════════════════════════════════════════════════════════════════════════════════╝
-- Temporary seeded accounts for local/dev login while invitation/auth flows are unstable.
-- Password hash is BCrypt for the team-provided seed password; do not store plaintext here.

WITH existing AS (
    SELECT id
    FROM institutions
    WHERE code = 'CIT-U'
       OR email_domain = 'cit.edu.ph'
    ORDER BY CASE WHEN code = 'CIT-U' THEN 0 ELSE 1 END
    LIMIT 1
),
updated AS (
    UPDATE institutions i
    SET name = 'Cebu Institute of Technology - University',
        code = 'CIT-U',
        email_domain = 'cit.edu.ph',
        status = 'active',
        updated_at = now()
    FROM existing
    WHERE i.id = existing.id
    RETURNING i.id
)
INSERT INTO institutions (name, code, email_domain, status)
SELECT 'Cebu Institute of Technology - University', 'CIT-U', 'cit.edu.ph', 'active'
WHERE NOT EXISTS (SELECT 1 FROM updated)
  AND NOT EXISTS (
      SELECT 1
      FROM institutions
      WHERE code = 'CIT-U'
         OR email_domain = 'cit.edu.ph'
  );

WITH cit AS (
    SELECT id FROM institutions WHERE code = 'CIT-U'
)
INSERT INTO users (
    email,
    password_hash,
    first_name,
    last_name,
    role,
    institution_id,
    account_state
)
SELECT
    seeded.email,
    seeded.password_hash,
    seeded.first_name,
    seeded.last_name,
    seeded.role,
    cit.id,
    'active'
FROM cit
CROSS JOIN (
    VALUES
        (
            'chrisdanielcabatana@gmail.com',
            '$2a$10$N4coBwAlcG3E1raYUW/E3OS7uk6ekBu.gQGxLnL9gK9Ubx7K7Fmxi',
            'Chris Daniel',
            'Cabataña',
            'validator'
        ),
        (
            'capunobriana@gmail.com',
            '$2a$10$N4coBwAlcG3E1raYUW/E3OS7uk6ekBu.gQGxLnL9gK9Ubx7K7Fmxi',
            'Briana Sophia',
            'Capuno',
            'contributor'
        )
) AS seeded(email, password_hash, first_name, last_name, role)
ON CONFLICT (email) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    role = EXCLUDED.role,
    institution_id = EXCLUDED.institution_id,
    account_state = 'active',
    updated_at = now();
