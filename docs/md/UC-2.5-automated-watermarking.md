# UC-2.5 Automated Watermarking

**Use Case ID:** UC-2.5
**Use Case Name:** Automated Watermarking
**Actor(s):** Administrator (configuration, write); any authenticated role (read, to render post previews).

**Precondition(s):** 
For configuration: the actor is an authenticated Administrator. 
For application: a submission containing at least one photo is being published.

## Main Flow — Watermark Configuration

1. An Administrator navigates to Page Settings and opens the Watermark configuration panel: a transparent canvas editor with 1:1, 4:5, and 16:9 aspect-ratio preview toggles.
2. The Administrator adds up to 3 elements — image, text, or shape (rectangle, line, or gradient). Each element is positioned and sized as percentage-based coordinates and can be dragged or resized.
3. The Administrator previews the design across the available aspect ratios.
4. The Administrator saves; the system stores each element's position and size as percentage values, along with its type-specific properties (image reference, text content and font, or shape type and color), serialized as JSON.

## Main Flow — Automatic Application

1. Watermarking is applied at the moment of publication, not at approval. This happens immediately for a Live Event Fast-Track submission, or at the scheduled time for a Standard submission. Because the configuration is read fresh at the moment of publishing, an Administrator updating the watermark design between a submission's approval and its actual publish time will affect that already-approved submission.
2. For each photo asset (JPEG, PNG, GIF, or WebP), the system reads the current network-wide watermark configuration, reads the photo's actual dimensions, and renders all configured elements at their proportionally scaled positions.
3. The watermarked version is generated and used for publishing. The original, unwatermarked asset remains unchanged in the media library, and the watermarked derivative is transiently stored to hand off the URL to the publisher.
4. Video assets are published without any watermark applied.

## Alternative Flow(s)

* **A1 — No Watermark Configured:** If no configuration exists at all, the system applies a built-in default text watermark (`@DASIGCentralVisayas`) rather than publishing unwatermarked. A submission publishes genuinely unwatermarked only if a configuration exists and has been explicitly disabled by an Administrator.
* **A2 — Update Existing Watermark Design:** An Administrator may edit the watermark configuration at any time. Because the design is read live at each publish attempt, an update takes effect on any submission not yet actually published.
* **A3 — Watermark Application Failure:** If rendering fails for any reason (an undecodable image, a download error, or any other exception in the render pipeline), the system publishes the original, unwatermarked image rather than blocking or delaying the post. Every failure is recorded in the immutable audit log and triggers an in-app and email notification to every Administrator, linking to the affected submission.
* **A4 — Mixed-Media Submission:** For a submission containing both photos and video, only the photo assets are watermarked; the video publishes without a watermark.
* **A5 — Per-Asset Watermark Opt-Out:** If the Contributor marked a specific photo as exempt during drafting, that opt-out is honored before any configuration lookup or rendering occurs — the asset publishes exactly as uploaded.

## Postcondition(s)

Approved photo submissions carry the network-wide watermark design, rendered proportionally to each photo's dimensions, at the moment they actually publish — except assets marked for opt-out. Original, unwatermarked assets remain unchanged in the media library. Video assets publish without a watermark. A rendering failure results in the unwatermarked original being published, always accompanied by an audit log entry and an Administrator notification.

## Implementation Status

* ✅ **Implemented:**
  * Administrator configures watermark (3 elements: image, text, shape).
  * Application at publication time.
  * A1 No Config -> default text watermark.
  * A2 Live reading of configuration.
  * A3 Watermark Application Failure logs and notifies admins. (Fixed a bug where built-in `dasig-logo.png` fallback was failing).
  * A4 Mixed-media accurately handled.
  * A5 Per-asset opt-out honored via `link.isSkipWatermark()`.
* ❌ **Not Implemented:** None found.
* ⚠️ **Discrepancies:**
  * **Shape Variants:** The original use case description listed "rectangle or line" for shapes, but the codebase and UI also successfully support and implement a "gradient" scrim variant for shapes.
  * **Supported Images:** The code supports watermarking for JPEGs, PNGs, GIFs, and WebPs, which is more specific than just "photo".

=========================================

# Original Version

Use Case ID
UC-2.5
Use Case Name
Automated Watermarking 
Actor(s)
Administrator 
Precondition(s)
For configuration: the actor is an authenticated Administrator.
For application: a submission containing at least one photo is being published. 
Main Flow
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
Alternative Flow(s)
A1 — No Watermark Configured: If no configuration exists at all, the system applies a built-in default text watermark rather than publishing unwatermarked. A submission publishes genuinely unwatermarked only if a configuration exists and has been explicitly disabled by an Administrator.
A2 — Update Existing Watermark Design: An Administrator may edit the watermark configuration at any time. Because the design is read live at each publish attempt rather than fixed at approval time, an update takes effect on any submission not yet actually published, including ones already approved and scheduled.
A3 — Watermark Application Failure: If rendering fails for any reason (an undecodable image, a download error, or any other exception in the render pipeline), the system publishes the original, unwatermarked image rather than blocking or delaying the post — a missing brand mark is treated as a minor cosmetic issue, not one worth risking a missed publish for. Every failure is recorded in the immutable audit log and triggers an in-app and email notification to every Administrator, linking to the affected submission.
A4 — Mixed-Media Submission: For a submission containing both photos and video, only the photo assets are watermarked; the video publishes without a watermark.
A5 — Per-Asset Watermark Opt-Out: If the Contributor marked a specific photo as exempt during drafting, that opt-out is honored before any configuration lookup or rendering occurs — the asset publishes exactly as uploaded.
Postcondition(s)
Approved photo submissions carry the network-wide watermark design, rendered proportionally to each photo's dimensions, at the moment they actually publish — except assets marked for opt-out. Original, unwatermarked assets remain unchanged in the media library. Video assets publish without a watermark. A rendering failure results in the unwatermarked original being published, always accompanied by an audit log entry and an Administrator notification.
