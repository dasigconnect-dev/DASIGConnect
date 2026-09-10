# UC-1.2 Institution Management

**Use Case ID:** UC-1.2

**Use Case Name:** Institution Management

**Actor(s):** Administrator (full access), Moderator (read-only)

**Precondition(s):** The actor holds a valid, authenticated ACTIVE session with Administrator or Moderator privileges. At least one Administrator account exists. A protected default institution, "DASIG Central Visayas," is seeded during initial system deployment for network-wide Administrator post attribution and cannot be deactivated (A6) or permanently deleted (A8) through this use case.

## Main Flow

1. The actor navigates to Institution Management from the admin console.
2. The system displays the institution list with each institution's name, status, and Contributor count. Moderators see this list in read-only form; action controls (Add Institution, Edit, Deactivate, Reactivate, Reassign, Delete) are visible only to Administrators.
3. *(Administrator only)* The Admin selects Add Institution and enters the institution's required identifying details.
4. The system validates the input, creates the institution workspace record, and establishes Row-Level Security scoping for that institution.
5. The new institution appears in the institution list and becomes available for Contributor assignment.

## Alternative Flows

- **A1 — Edit Institution Details:** *(Administrator only)* The Admin updates an existing institution's details.
- **A2 — Deactivate Institution:** *(Administrator only)* The Admin attempts to deactivate an institution. The system checks whether any Contributors are currently assigned:
  - If Contributors remain assigned, deactivation is blocked and the system prompts the Admin to reassign each remaining Contributor to another institution first (A4).
  - If the institution has no assigned Contributors, it deactivates: the system prevents new Contributor invitations to it and retains historical submissions and media for audit purposes.
- **A3 — Reactivate Institution:** *(Administrator only)* The Admin reactivates a previously deactivated institution, restoring its ability to receive Contributor invitations.
- **A4 — Reassign Institution User:** *(Administrator only)* The Admin moves an existing Contributor to another institution when the transfer is valid (target institution must be `ACTIVE`). The system updates tenant scoping for future actions while historical submissions remain attributed to the original institution.
- **A5 — Duplicate Institution Name:** The system rejects creation or rename with a validation error. The same validation also applies to a duplicate institution code or email domain.
- **A6 — Attempted Deactivation of Protected Institution:** The system blocks deactivation of DASIG Central Visayas, since it is required for network-wide Administrator post attribution (UC-1.5) and default watermark configuration (UC-2.5).
- **A7 — Moderator Attempts a Restricted Action:** If a Moderator attempts any action beyond viewing (e.g., via a direct API call), the system rejects the request with an authorization error; the action controls are not exposed in the Moderator's view in the first place.
- **A8 — Permanent Institution Deletion:** *(Administrator only)* The Admin selects Delete on an institution. The system requires the Admin to type the institution's code exactly to confirm before proceeding; the Delete action remains disabled until the typed value matches. The system blocks the action if any of the following are true, returning a validation error directing the Admin to resolve the blocker first:
  - The institution is the protected network default (DASIG Central Visayas) — it can never be permanently deleted, matching its protection from deactivation (A6).
  - Any Contributor account exists for the institution in `PENDING`, `PENDING_EMAIL_UNDELIVERED`, or `ACTIVE` state (the Admin must transfer or remove these accounts first).
  - Any submission — of any state, including terminal states — has ever existed for the institution.
  - Any non-deleted media asset exists in the institution's library.

  If none of these conditions apply, the system:
  - Deletes institution-scoped invitation tokens, slot reservations, page settings, and watermark configurations.
  - Deletes any soft-deleted media assets and their albums (retained for audit up to this point, since they still carry the `institution_id` reference).
  - Permanently removes the institution record.
  - Best-effort purges the corresponding storage objects after the transaction commits; a storage purge failure does not roll back or fail the deletion.
  - Records an `INSTITUTION_DELETED` audit entry with the institution's name and code.

  This action is irreversible and distinct from Deactivate (A2), which is reversible and retains all data. Deletion does not check for Moderator or Administrator association, since neither role is institution-scoped.

## Postcondition(s)

An institution record has been created, edited, deactivated, reactivated, or permanently deleted (if eligible); or a Contributor has been reassigned, with per-institution data isolation enforced accordingly.

---

_Verified against the running code as of 2026-09-10. Primary sources: `InstitutionService` (`createInstitution`, `updateInstitution`, `deactivateInstitution`, `reactivateInstitution`, `deleteInstitution`), `InstitutionController`, `UserService.reassignContributor`. Contributor assignment to the protected default institution is intentionally unrestricted — the earlier "excluded from normal Contributor assignment" claim for Main Flow step 5 was found unenforced in code and removed by decision; only UC-1.5's Posting-As dropdown excludes it._
