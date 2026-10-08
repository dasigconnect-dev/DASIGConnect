# UC-1.8 Engagement Helpers

**Use Case ID**
UC-1.8

**Use Case Name**
Engagement Helpers 

**Actor(s)**
Contributor, Moderator, Administrator — all three use the same composer (UC-1.5). The panel is skipped for an Administrator composer until an institution scope has been selected, because guard-rail evaluation requires an institution context. Moderators are network-wide and are not blocked by an institution scope requirement. 

**Precondition(s)**
The actor is in an active composer session (UC-1.5), on the Organize & Schedule step, with a Standard (non-Fast-Track) draft. Live Event Fast-Track drafts skip scheduling entirely and never see this panel. 

## Main Flow
1. The actor opens the scheduling panel within Organize & Schedule; it loads automatically for a Standard draft, with no separate "open" action.
2. The system queries recent historical social media engagement for the DASIG Page. It analyzes recent post performance to identify high-engagement periods. If sufficient historical data exists, it recommends optimal posting windows.
3. Samples are bucketed by day-of-week and hour, restricted to the organization's approved daytime posting window, averaged per bucket, and the top 5 buckets become candidate windows — provided at least 20 samples exist; otherwise a fixed default of Tuesday/Wednesday/Thursday 6 PM is used (A1).
4. For each candidate window, the system walks the next 30 days for the first matching weekday, validates it against the same scheduling guard rails as a manual pick, and keeps it only if not hard-blocked — up to 3 recommended slots total, each labeled with a single-hour window (e.g., "Best engagement: Tuesdays 6 PM - 7 PM") and carrying any soft warnings and a rounded score.
5. The actor selects a recommended slot (filling the date/time fields, same as typing manually), or ignores the recommendation and chooses a custom date/time in the standard picker shown below the panel.
6. The system validates the selected slot — recommended or manual — against the same scheduling rules. When guard rails are enabled, conflicts within ±30 minutes, insufficient lead time, and times outside the Administrator-configured posting window are hard-blocked. The six-post daily threshold is surfaced as a soft warning and does not block scheduling. Validation is debounced as the actor changes the date or time.
7. The selected schedule is saved to the draft on the next autosave/save, same as any other field.

## Alternative Flow(s)
- **A1 — Insufficient Historical Data**: Fewer than 20 samples (or no engagement in the Administrator-configured posting window at all) falls back to the fixed default (Tuesday/Wednesday/Thursday 6 PM, descending weight), shown in the panel as "Best-practice guidance" with a notice that recommendations will improve as more Facebook history accumulates.
- **A2 — Recommended Slot Conflicts with Guard Rail**: A hard-blocked candidate slot (e.g., inside another post's ±30-minute buffer) is excluded from the list entirely — not shown with a "blocked" annotation.
- **A3 — Analytics Service Unavailable**: If analytics data is unavailable due to integration or network issues, results in the panel renders nothing; the date/time picker underneath is unaffected.
- **A4 — Manual Override of Recommendation**: The actor may pick any non-recommended time, blocked only by a hard guard rail (same as a recommended slot). If it clears the hard block but carries a soft warning, the composer shows it inline beneath the date/time fields, the same treatment used for the mixed-media notice (UC-1.7 A7). This applies only when the network-wide guard-rail toggle is enabled.

## Postcondition(s)
The draft has an assigned scheduled date and time, either selected from a system recommendation or entered manually. When guard rails are enabled, the time is checked against the applicable hard rules and soft warnings; when disabled, the guard-rail restrictions are skipped. Standard posts still require a future scheduled time and slot reservation. Fast-Track posts do not require a scheduled time or reservation.
