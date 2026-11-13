-- Exact calendar collisions are never allowed, even when configurable guard
-- rails are disabled. Spacing remains controlled by the per-reservation GiST
-- interval from V134; this partial unique index protects only identical times.
CREATE UNIQUE INDEX IF NOT EXISTS ux_slot_reservations_active_exact_time
ON slot_reservations (scheduled_at)
WHERE status <> 'released';
