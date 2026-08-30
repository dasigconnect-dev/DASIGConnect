-- Removes the UC-3.5 Category C "Guard Rail Override Request" table.
--
-- The standalone Resolution Center that owned the request -> admin-decision
-- round-trip was removed. Guard-rail overrides now happen inline: a reviewer
-- reschedules a blocked slot with a typed reason (handled in ValidationService
-- / SubmissionService), so nothing reads or writes override_requests any more.
--
-- V24 created this table; no other table references it, so a plain drop is safe.
DROP TABLE IF EXISTS override_requests CASCADE;
