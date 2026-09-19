-- V95's network-wide GR-H1 exclusion constraint closed a real race (two
-- active reservations could land within 30 minutes of each other), but it
-- was unconditional -- it also silently defeated the Administrator's
-- pre-existing, intentional guard-rail override capability on reschedule
-- (SubmissionService.reschedule) and manual-publish retry-with-new-schedule
-- (ManualPublishingService.retryWithNewSchedule): both already let an Admin
-- explicitly override a blocked GR-H1 slot with a reason, via
-- SlotReservationService.reserveLockedSlot(), whose own javadoc says it
-- "bypasses guard rail validation (caller is responsible)". After V95, that
-- bypass no longer worked -- the INSERT hit the DB constraint anyway,
-- regardless of the override.
--
-- Fix: mark a reservation created via an explicit admin override, and exempt
-- only those rows from the constraint. A third submission later scheduled
-- near an overridden slot is still normally blocked (unless it's also an
-- override) -- this only reopens the narrow race for the override case
-- itself, which is no worse than before V95 ever existed there.
ALTER TABLE slot_reservations
ADD COLUMN IF NOT EXISTS admin_override BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE slot_reservations
DROP CONSTRAINT excl_slot_reservations_network_buffer;

ALTER TABLE slot_reservations
ADD CONSTRAINT excl_slot_reservations_network_buffer
EXCLUDE USING gist (
    int8range(
        epoch_seconds_immutable(scheduled_at),
        epoch_seconds_immutable(scheduled_at) + 1800,
        '[]'
    ) WITH &&
) WHERE (status <> 'released' AND NOT admin_override);
