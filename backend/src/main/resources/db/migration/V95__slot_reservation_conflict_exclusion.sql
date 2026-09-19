-- GR-H1 (network-wide, no two active posts within ±30 minutes of each other)
-- was only ever enforced by a pre-write read in GuardRailService, never by the
-- database -- SlotReservationService's own comments claimed a "unique index
-- on (scheduled_at, institution_id)" already existed, but V1 only ever
-- created a plain, non-unique index. Two concurrent requests could both pass
-- the read-check before either wrote, so nothing actually stopped a real
-- conflict from landing in the table.
--
-- Cleanup first: a submission's locked SlotReservation was never released
-- once the submission actually published (see FacebookPublisherService and
-- ManualPublishingService, fixed alongside this migration to call
-- SlotReservationService.release() on success) -- so terminal, already-
-- published submissions have been accumulating permanently "locked" slots
-- that serve no purpose and can spuriously conflict with unrelated future
-- slots under the new constraint below. Release those first.
UPDATE slot_reservations sr
SET status = 'released'
FROM submissions s
WHERE sr.submission_id = s.id
  AND sr.status = 'locked'
  AND s.status IN ('published', 'published_manual', 'admin_direct_post');

-- The actual fix: a network-wide exclusion constraint so the database itself
-- rejects any two active (non-released) reservations within 30 minutes of
-- each other, no matter how the requests race. Mirrors the existing
-- GuardRailService.existsActiveWithin30Minutes query's inclusive semantics
-- (BETWEEN slot-30min AND slot+30min) via a closed-closed range overlap.
-- No institution_id term -- GR-H1 is deliberately network-wide.
--
-- Can't build the range directly on `scheduled_at + interval '30 minutes'`:
-- Postgres marks timestamptz + interval as STABLE, not IMMUTABLE (it can, in
-- general, depend on the session timezone), and an index/exclusion
-- expression must be IMMUTABLE. A timestamptz's Unix epoch value is,
-- unlike that general operator, genuinely timezone-independent -- so wrap
-- EXTRACT(EPOCH ...) in our own function and assert IMMUTABLE ourselves,
-- then build the range on whole seconds (int8range) instead of tstzrange.
CREATE OR REPLACE FUNCTION epoch_seconds_immutable(ts timestamptz)
RETURNS bigint
LANGUAGE sql
IMMUTABLE
PARALLEL SAFE
AS $$
    SELECT (EXTRACT(EPOCH FROM ts))::bigint;
$$;

ALTER TABLE slot_reservations
ADD CONSTRAINT excl_slot_reservations_network_buffer
EXCLUDE USING gist (
    int8range(
        epoch_seconds_immutable(scheduled_at),
        epoch_seconds_immutable(scheduled_at) + 1800,
        '[]'
    ) WITH &&
) WHERE (status <> 'released');
