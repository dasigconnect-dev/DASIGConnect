-- Guard-rail override requests are removed. A hard block is now a hard block for
-- contributors and moderators; only an administrator can bypass it (inline, with
-- a reason, recorded in the audit log). Nothing reads or writes override_requests
-- any more.
--
-- V24 created this table; no other table references it, so a plain drop is safe.
DROP TABLE IF EXISTS override_requests CASCADE;
