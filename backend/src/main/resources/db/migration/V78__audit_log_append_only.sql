-- V78: audit_log becomes a proper "Detective Control" record.
--
-- DASIG uses fixed admin roles and relies on accountability rather than a
-- dynamic RBAC matrix: every administrator has the same capabilities, but every
-- action is permanently recorded here, and the Super Administrator uses this log
-- to identify and act on abuse. For that to work the log must be:
--
--   1. append-only     — ANY authenticated context may add an entry, including
--                        pre-auth flows (login, password reset) and scheduled
--                        jobs that record system actions with NO tenant scope
--                        bound and NO actor (actor_id IS NULL). The old policy
--                        silently rejected every one of those inserts, so whole
--                        classes of events (auth, token health, abandonment
--                        sweeps, retention purges) never reached the log.
--   2. fully visible   — every network administrator (and the super
--                        administrator, whose scope role resolves to
--                        'administrator') sees the ENTIRE log, system rows
--                        included. Institution-scoped users keep the narrow read
--                        they already had: entries whose actor is in their
--                        institution (this backs the per-asset Activity tab).
--   3. immutable       — no UPDATE or DELETE through the application connection.
--                        The old "FOR ALL" policy let an administrator silently
--                        mutate or erase entries.

DROP POLICY IF EXISTS audit_log_tenant_isolation ON audit_log;
DROP POLICY IF EXISTS audit_log_append ON audit_log;
DROP POLICY IF EXISTS audit_log_read ON audit_log;

-- (1) Append-only. Integrity comes from the absence of UPDATE/DELETE policies,
--     not from constraining who may INSERT.
CREATE POLICY audit_log_append ON audit_log
    FOR INSERT
    WITH CHECK (true);

-- (2) Read: network admins see everything; institution-scoped users see only
--     entries authored by someone in their own institution.
CREATE POLICY audit_log_read ON audit_log
    FOR SELECT
    USING (
        current_setting('app.current_role', true) = 'administrator'
        OR EXISTS (
            SELECT 1 FROM users u
            WHERE u.id = audit_log.actor_id
              AND u.institution_id = nullif(current_setting('app.current_institution_id', true), '')::UUID
        )
    );

-- (3) No FOR UPDATE / FOR DELETE policy is created, so those commands are denied
--     for every role the application connects as.
