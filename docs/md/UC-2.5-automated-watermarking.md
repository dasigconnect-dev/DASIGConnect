# UC-2.5 Automated Watermarking

**Use Case ID:**
UC-2.5
**Use Case Name:**
Automated Watermarking 
**Actor(s):**
Administrator 
**Precondition(s):**
For configuration: the actor is an authenticated Administrator.
For application: a submission containing at least one photo is being published. 
**Main Flow:**
Watermark Configuration
An Administrator navigates to Page Settings and opens the Watermark configuration panel: a transparent canvas editor with 1:1, 4:5, and 16:9 aspect-ratio preview toggles.
The Administrator adds up to 3 elements — image, text, or shape (rectangle or line). Each element is positioned and sized as percentage-based coordinates and can be dragged or resized.
The Administrator previews the design across the available aspect ratios.
The Administrator saves; the system stores each element's position and size as percentage values, along with its type-specific properties (image reference, text content and font, or shape type and color).
Automatic Application
Watermarking is applied at the moment of publication, not at approval. This happens immediately for a Live Event Fast-Track submission, or at the scheduled time for a Standard submission. Because the configuration is read fresh at the moment of publishing, an Administrator updating the watermark design between a submission's approval and its actual publish time will affect that already-approved submission — the design in effect at publish time is always the one applied, not whatever was configured at approval.
For each photo asset (JPEG, PNG, GIF, or WebP), the system reads the current network-wide watermark configuration, reads the photo's actual dimensions, and renders all configured elements at their proportionally scaled positions.
The watermarked version is generated and used for publishing; the original, unwatermarked asset remains unchanged in the media library.
Video assets are published without any watermark applied.
**Alternative Flow(s):**
A1 — No Watermark Configured: If no configuration exists at all, the system applies a built-in default text watermark rather than publishing unwatermarked. A submission publishes genuinely unwatermarked only if a configuration exists and has been explicitly disabled by an Administrator.
A2 — Update Existing Watermark Design: An Administrator may edit the watermark configuration at any time. Because the design is read live at each publish attempt rather than fixed at approval time, an update takes effect on any submission not yet actually published, including ones already approved and scheduled.
A3 — Watermark Application Failure: If rendering fails for any reason (an undecodable image, a download error, or any other exception in the render pipeline), the system publishes the original, unwatermarked image rather than blocking or delaying the post — a missing brand mark is treated as a minor cosmetic issue, not one worth risking a missed publish for. Every failure is recorded in the immutable audit log and triggers an in-app and email notification to every Administrator, linking to the affected submission.
A4 — Mixed-Media Submission: For a submission containing both photos and video, only the photo assets are watermarked; the video publishes without a watermark.
A5 — Per-Asset Watermark Opt-Out: If the Contributor marked a specific photo as exempt during drafting, that opt-out is honored before any configuration lookup or rendering occurs — the asset publishes exactly as uploaded.
**Postcondition(s):**
Approved photo submissions carry the network-wide watermark design, rendered proportionally to each photo's dimensions, at the moment they actually publish — except assets marked for opt-out. Original, unwatermarked assets remain unchanged in the media library. Video assets publish without a watermark. A rendering failure results in the unwatermarked original being published, always accompanied by an audit log entry and an Administrator notification. 

=========================================

# Developer / Technical Notes

## Implementation Status
* ✅ **Implemented:**
  * **Configuration Panel:** 1:1, 4:5, 16:9 previews, drag/drop sizing.
  * **Element Types:** Image, text, and shape configuration up to a max of 3 elements. 
  * **Application Timing:** Configurations are read and applied live at the actual publish time, not approval time.
  * **A1 Default Watermark:** A built-in text watermark (`@DASIGCentralVisayas`) correctly kicks in if no DB config exists.
  * **A3 Failure Handling:** Failures do not block publishing. The original unwatermarked image is published, an audit log `PUBLISH_WATERMARK_FAILED` is recorded, and a notification is sent to administrators.
  * **A4 Mixed-Media:** Videos are cleanly bypassed.
  * **A5 Opt-Out:** Assets explicitly flagged to bypass watermarking are skipped correctly.
* ❌ **Not Implemented:**
  * None found.
* ⚠️ **Discrepancies:**
  * **Shape Variants:** The core document says "rectangle or line" for shapes, but the underlying codebase supports and implements a `gradient` scrim variant as well. This is fully working in the app.
  * **Image Formats:** The code is explicit about formats (JPEGs, PNGs, GIFs, and WebPs) rather than just "photos".

## Technical Changes Made (2026-10-10)
* **Bug Fix (Backend):** Fixed a logic dissonance where the `WatermarkApplicationService` could not load the default `dasig-logo.png` because it didn't exist in the backend classpath and was incorrectly skipped. Added the logo to backend `resources/` and properly handled the fallback.
* **Bug Fix (Frontend):** Fixed `WatermarkOverlay.tsx` edge-case logic where it was explicitly hardcoded to swap out the valid `"/dasig-logo.png"` source for `"/favicon.svg"`, leading to visual inconsistencies in the editor canvas.
