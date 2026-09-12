# UC-1.8 Engagement Helpers

**Use Case ID:** UC-1.8

**Use Case Name:** Engagement Helpers

**Actor(s):** Contributor, Moderator, Administrator — all three use the same composer (UC-1.5); the recommendation endpoint is `hasAnyRole('CONTRIBUTOR','MODERATOR','ADMIN')`, and the panel is only skipped for an Admin composer that hasn't picked an institution scope yet (guard rails can't be evaluated without one).

**Precondition(s):** The actor is in an active composer session (UC-1.5), on the **Organize & Schedule** step, with a Standard (non-Fast-Track) draft. Confirmed accurate: the recommendation query and the whole recommendations panel are both gated on `!form.fastTrack` — Live Event Fast-Track drafts skip scheduling entirely and never see this panel.

## Main Flow

1. The actor opens the scheduling panel within the composer's Organize & Schedule step (the panel loads automatically for a Standard draft — there's no separate "open" action).
2. The system queries **real historical Facebook engagement** for the DASIG Page: the last 100 posts' `created_time` + reactions + comments + shares (`FacebookEngagementAnalyticsClient.fetchRecentPostEngagement`, Graph API), scored as `reactions + comments + shares` per post.
3. Samples are bucketed by (day-of-week, hour) restricted to **8 AM–8 PM** local time (`Asia/Manila`), averaged per bucket, and the top 5 buckets become candidate windows — provided there are at least **20** samples total; otherwise a fixed default of Tue/Wed/Thu 6 PM is used (see A1).
4. For each candidate window, the system walks the next **30 days** looking for the first matching weekday, checks it against the same scheduling guard rails as a manual pick (`GuardRailService.validate`), and keeps it only if not hard-blocked — up to **3** recommended slots total, each labeled e.g. "Best engagement: Tuesdays 6 PM - 7 PM" (a single-hour window, not the two-hour example in the prior draft) and carrying any soft warnings and a rounded score.
5. The actor selects a recommended slot (`onSelect` fills the date/time fields, same as typing them manually), or ignores the recommendation and manually chooses a custom date/time in the standard picker shown right below the panel.
6. The system validates the selected slot — recommended or manual — against the same guard rails (conflict prevention, lead time, publish window, daily cap) via `POST /guardrails/validate`, debounced on every change to `scheduledAt`.
7. The selected schedule is saved to the draft on the next autosave/save, same as any other field.

## Alternative Flows

- **A1 — Insufficient Historical Data:** confirmed. Fewer than 20 samples (or no engagement in the 8 AM–8 PM window at all) falls back to a fixed default (Tuesday/Wednesday/Thursday 6 PM, descending weight) with `source: "DEFAULT"` and the notice "Using general weekday evening guidance. Recommendations will improve as more Facebook history is collected." — shown in the panel as "Best-practice guidance" instead of "N Facebook posts analyzed."
- **A2 — Recommended Slot Conflicts with Guard Rail:** confirmed, and specifically the **exclude** branch, not the **flag** branch — a hard-blocked candidate slot (e.g. inside another post's ±30-min buffer) is simply skipped and never appears in the list; it is not shown with a "blocked" annotation.
- **A3 — Analytics Service Unavailable:** confirmed. Any failure fetching Facebook data (no Page token configured, Graph API error, network failure) returns `available: false`; the frontend maps that straight to `null` and the panel renders nothing (`EngagementRecommendationsPanel` returns `null` when `recommendations` is falsy) — the date/time picker underneath is unaffected either way.
- **A4 — Manual Override of Recommendation:** **partially implemented.** The actor can pick any non-recommended time, and it's blocked only by a hard guard rail (same as a recommended slot) — that half is correct. But the "may display a soft warning" half does **not** happen here: `GuardRailResult.softWarnings` from the manual-pick validation is stored in state and used only to decide whether the slot *counts as ready* — the warning text itself is never rendered anywhere in this composer. (Soft warnings **are** rendered elsewhere in the app — `ValidationQueueScreen` / `ResolutionRetryModal`'s reschedule flow for reviewers — just not here.) The *recommended*-slot buttons do show a warning (`slot.warnings[0]`) when a recommendation itself carries one, so the gap is specific to a manually-typed time.

## Postcondition(s)

The draft has an assigned scheduled date/time, either selected from a system recommendation or chosen manually by the actor, validated against the same scheduling guard rails either way. Confirmed accurate.

---

## Corrections from the prior draft of this UC

- **Actors:** added Administrator — the composer (and this panel) is shared by all three roles, same as UC-1.5/1.6/1.7; there's no separate "Moderator acting as Contributor" mode.
- **"Per-institution quotas" is the wrong scope** for the ≤6-posts/day guard rail referenced here — `GuardRailService.GR_S2_MAX_PER_DAY` is enforced **network-wide** (`slotReservationRepository.countActiveOnDay`, no institution filter), same nuance already corrected in UC-1.5. The ±30-minute buffer (GR-H1) is likewise network-wide, not per-institution.
- **A4's soft-warning half is not implemented in the composer** — see above. This is the one real gap found; everything else in the Main Flow and A1–A3 matched the running code closely, including the exact sample-size threshold (20), the lookback window (100 posts / 8 AM–8 PM), the slot cap (3), and the lookahead window (30 days), none of which were in the prior draft.
- The example label "Best engagement: Tuesdays 6–8 PM" implies a 2-hour window; the actual generated label is a single hour (e.g. "Tuesdays 6 PM - 7 PM").

---

_Verified against the running code as of 2026-09-12. Primary sources: `EngagementRecommendationService` (`MINIMUM_SAMPLE_SIZE`, `DEFAULT_WINDOWS`, `MAX_SLOTS`, `buildSlots`, `PAGE_ZONE`), `FacebookEngagementAnalyticsClient.fetchRecentPostEngagement`, `EngagementRecommendationController` (`GET /engagement-recommendations`), `EngagementRecommendationServiceTest` (`insufficientHistoryReturnsDefaultSlots`, `analyticsFailureHidesRecommendations`, `hardBlockedCandidatesAreExcluded`, `sufficientHistoryUsesFacebookEngagementWindows`), `GuardRailService` (`GR_S2_MAX_PER_DAY`, GR-H1 network-wide framing), `frontend/src/features/submission/components/EngagementRecommendationsPanel.tsx`, `SubmissionScreen.tsx` (`shouldLoadEngagementRecommendations`, `applyEngagementSlot`, the guard-rail validation `useEffect`), `frontend/src/features/validation/ValidationQueueScreen.tsx` / `ResolutionRetryModal.tsx` (where `softWarnings` actually is rendered)._
