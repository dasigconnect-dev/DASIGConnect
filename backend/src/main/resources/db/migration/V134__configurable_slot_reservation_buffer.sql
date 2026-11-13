ALTER TABLE slot_reservations
ADD COLUMN IF NOT EXISTS conflict_buffer_minutes INT NOT NULL DEFAULT 30
CHECK (conflict_buffer_minutes >= 0);

UPDATE slot_reservations
SET conflict_buffer_minutes = COALESCE(
    (SELECT conflict_buffer_minutes
     FROM page_settings
     WHERE institution_id IS NULL
     LIMIT 1),
    30
)
WHERE status <> 'released';

ALTER TABLE slot_reservations
DROP CONSTRAINT IF EXISTS excl_slot_reservations_network_buffer;

-- Half-open ranges make a slot exactly one configured interval away valid:
-- with a 20-minute interval, 08:20 and 08:40 do not overlap.
ALTER TABLE slot_reservations
ADD CONSTRAINT excl_slot_reservations_network_buffer
EXCLUDE USING gist (
    int8range(
        epoch_seconds_immutable(scheduled_at),
        epoch_seconds_immutable(scheduled_at) + conflict_buffer_minutes * 60,
        '[)'
    ) WITH &&
) WHERE (status <> 'released' AND NOT admin_override);

-- conflict_buffer_minutes is a creation-time policy snapshot. Changing the
-- network setting must not rewrite existing reservations: increasing it could
-- retroactively make two previously valid slots overlap and reject the entire
-- settings transaction. Suggestions and new reservations use the latest value.
