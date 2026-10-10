Use Case ID
UC-3.1

Use Case Name
Master Calendar Visibility 

Actor(s)
Contributor, Moderator, Administrator

Precondition(s)
The actor is authenticated. For reschedule: the target submission must currently be scheduled. 

Main Flow
Calendar Visibility 
Any authenticated actor opens the calendar.
Moderator or Administrator: sees every submission with a scheduled slot or publish time, network-wide, always rendered in full detail (caption, description, contributor identity, everything) — Moderator and Administrator have identical calendar visibility, with no scoping difference between them.
Contributor: sees two combined sets of events:
A network view, rendered in full detail for their own institution and the network-default institution ("DASIG Central Visayas"), and masked (caption, description, and contributor identity withheld) for every other institution.
Their own in-flight submissions — including states not otherwise visible to anyone else on the calendar (Pending, In Review, Publish Failed, Missed Review) — always shown in full detail and clearly marked as their own, so a Contributor can track their own submissions through the whole pipeline even before they become calendar-visible to others.

Reschedule (Moderator/Administrator only) 
The actor drags a scheduled event to a new date/time.
The system performs quick client-side checks (rejecting a same-slot drop or a drop within 1 hour of the current time).
For a Moderator: the interface also pre-checks the reschedule cap and window (see A1) before allowing the action to proceed, giving fast feedback without a server round-trip. Administrators are exempt from both.
A confirmation dialog opens requiring a written justification — this applies to every reschedule, regardless of whether the new slot triggers a guard rail warning, so that every calendar change carries a stated reason in the audit trail, not just contested ones. 
On confirmation, the system re-validates on the server: the submission must still be scheduled, a Moderator's cap/window is re-checked authoritatively (the client-side check is a convenience, not the real enforcement), and the new slot is validated against the guard rails.
If the guard rails are satisfied, or the actor is an Administrator supplying an override reason, the reschedule proceeds as an atomic, conflict-safe write (see A3).
The slot is reserved, the Contributor is notified, and the updated event appears on the calendar.

Alternative Flow(s)
A1 — Moderator Reschedule Cap & Window: A Moderator may reschedule a given submission at most 2 times, and only within ±1 day of the slot it was originally approved at (not the most recently rescheduled slot — anchoring to the original approval prevents drift through repeated small moves). Either limit is a hard stop for a Moderator, with no override available to them; they must ask an Administrator, who is exempt from both limits. If a failed publication is later retried onto a new schedule, both the count and the anchor slot reset, since that retry is effectively a fresh start.
A2 — Guard Rail Hard Block on Reschedule: If the new slot violates a hard guard rail, a Moderator cannot proceed under any circumstances, even with a written reason, and is directed to ask an Administrator. Only an Administrator may supply an override reason and proceed; doing so is recorded in the audit log with the violated rules, both the original and new slot, and the stated reason.
A3 — Concurrent Reschedule Protection: Reschedule writes are atomic and conflict-safe: if two reschedule attempts for the same submission are made at nearly the same time, only one succeeds: the other is rejected with a clear conflict message rather than silently allowed to overwrite or bypass a Moderator's cap.
A4 — Slot Conflict Integrity (Network-Wide, GR-H1): The ±30-minute network-wide conflict buffer between any two active posts is enforced at the database level, not just at the point a request is validated, so it cannot be bypassed by a timing race between two near-simultaneous requests. A submission's held slot is released once that submission actually publishes, so a completed post no longer occupies its buffer window indefinitely. This database-level enforcement includes one deliberate, narrowly scoped exemption: when an Administrator exercises their guard-rail override authority (A2) to reschedule into an otherwise-conflicting slot, that specific reservation is explicitly flagged as an authorized override and is excluded from the database-level block — every other reservation, including any other Administrator or Moderator action, remains fully enforced with no exception. 

Postcondition(s)
Every actor sees a calendar reflecting their role's true scope — Administrator and Moderator see the full network in full detail; Contributor sees their own institution and the network-default institution in full detail, every other institution masked, plus their own in-flight submissions regardless of status. A scheduled submission may be moved by a Moderator (within their cap and window) or an Administrator (unrestricted, with override justification where a guard rail is violated), with every write guaranteed correct even under concurrent requests — including protection against a race with any other path that could claim the same slot, not only another reschedule attempt — and network-wide slot-conflict integrity enforced independently of the application layer, except for the single, explicitly tracked case of an authorized Administrator override. 

=========================================

## Developer / Technical View

### Implementation Status

**✅ Implemented:**
- **Role-based Scope Logic:** Implemented perfectly in `CalendarService.getCalendarEvents()`. Moderatores and Admins use `getAdminCalendar()`. Contributors get `getScopedCalendar()` which correctly merges a network bucket (masking all except own institution and DASIG Central Visayas) and an own-workflow bucket (with `mine: true`).
- **Reschedule Checks:** Implemented in `SubmissionService.reschedule()`. It correctly enforces the `SubmissionStatus.scheduled` status, moderator cap (`MODERATOR_MAX_RESCHEDULES`), and the ±1 day drift (`MODERATOR_RESCHEDULE_WINDOW`). 
- **Reschedule Justification:** The frontend's `CalendarRescheduleModal` forces a user to input a reason. `GuardRailService` validation correctly throws `GuardRailViolationException` if blocked, and `auditLogService.record` safely logs an admin override. 
- **Atomic Operations and DB constraints:** The `slot_reservations` PostgreSQL GiST constraint is implemented network-wide (`V95__slot_reservation_conflict_exclusion.sql` & `V96__slot_reservation_admin_override_exemption.sql`), ensuring strict `EXCLUDE USING gist` for slot overlaps unless flagged with `admin_override`. 
- **Atomic Claiming:** Implemented in the repository where concurrent reschedules result in a 409 CONFLICT if a race condition occurs.

**❌ Not Implemented:**
- None.

**⚠️ Discrepancies:**
- None.

### Technical Notes
- The `isAdmin` variable used in the frontend's `CalendarEventDetailModal.tsx` actually means "Moderator or Administrator" (based on `user.role === "moderator" || user.role === "admin"`). It operates correctly but has a misleading name.
- The use case of automated post publishing that transitions the schedule state into `published` or `publish_failed` has been separated into `UC-3.2-automated-facebook-post-publishing.md`.
