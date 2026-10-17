# **MVP Validation Findings & Refactoring Priorities**

# **Section 1 — Project Information**

1. **Team/Group Name:** 2627-sem1-it411-38  
2. **Project/System Title:** DASIGConnect — A centralized platform for coordinating, validating, and scheduling DASIG social media content across multiple member institutions  
3. **Program / Section:** BS Information Technology — IT332 G1  
4. **Team Members:** Richemmae V. Bigno, Jay Lord C. Bayonas, Chris Daniel P. Cabataña, Mark Anton L. Camoro, Lerah A. Caones  
5. **Adviser:** Ralph Laviste  
6. **System URL / MVP Link:** [`dasigconnect.com`](http://dasigconnect.com)   
7. **Date of MVP Validation:** 13–21 September 2026  
8. **Number of respondents/participants:**  
   - 15 participants submitted 16 responses. One participant tested two roles.  
   - By role: 8 Contributors, 5 Moderators, 3 Administrators.  
9. **Types of respondents involved:**  
   - ☑ **End User:** 7 participants operated the system hands-on.  
   - ☑ **Decision Maker:** 9 participants evaluated whether an organization like DASIG should adopt the system.  
   - ☑ **Other:** representatives from DASIG member institutions, acting in the Contributor, Moderator, and Administrator roles.

## **Section 2 — MVP Validation Overview**

1. **Primary purpose of the MVP validation**  
   We wanted to learn whether people in each of DASIGConnect's three roles could complete their core workflow on the deployed MVP, and how usable and useful they found it. The workflows were:  
     
* **Contributors:** submit a complete, scheduled post.  
* **Moderators:** review it.  
* **Administrators:** provision institutions and users, then monitor publication.  
    
  The results show whether the MVP's current approach can realistically reach the proposal's measurable outcomes:  
    
* ≥95% form completion on the first submission attempt (GO1, SO 1.7).  
* SUS ≥70 across the three roles (GO2, RQ3).  
* ≥60% acceptance of AI caption suggestions, generated for ≥90% of uploaded images, with average quality ≥4.0/5 (GO3, RQ5).  
    
  They also show where the requirements or design should change before full implementation.  
2. **Validation framework/model used**  
   * **Framework/Model:** ISO 9241-11, which defines usability as effectiveness, efficiency, and satisfaction in a specified context of use. Supporting measures: Task Success Rate, Time-on-Task, post-task satisfaction, the System Usability Scale (SUS), and a TAM-informed perceived-usefulness lens for the AI features.  
   * **Key constructs/criteria evaluated:**  
     * **Effectiveness:** task results (independent / with help / unable / not performed).  
     * **Efficiency:** self-reported task minutes and media-search seconds.  
     * **Satisfaction:** role rating grids, post-task satisfaction (PS1–PS4), and SUS1–SUS10.  
     * **AI acceptance:** exposure, caption acceptance behaviour, caption quality, relevance of the first recommended media asset, and perceived usefulness.  
     * **Context of use:** role, prior use, perspective, and device.  
   * **Why this framework is appropriate:**  
     * DASIGConnect is a role-based operational workflow. Success means each role can finish its real task efficiently and acceptably, and ISO 9241-11 measures exactly that.  
     * The proposal's Validation Approach already names ISO 9241-11, Time-on-Task, and SUS.  
     * SUS supplies the benchmark used in GO2 (≥70).  
     * The TAM-informed items measure the acceptance behind GO3's adoption target.  
3. **How the validation was conducted**  
   * **Who participated:**  
     * 15 participants with experience handling Facebook pages, each assigned one role. One person tested both the Contributor and Moderator roles.  
     * 11 of the 16 sessions were the participant's first time using DASIGConnect.  
   * **How they interacted with the MVP:**  
     * Each participant used the deployed system on their own device: 8 desktop/laptop, 5 mobile phone, 3 tablet.  
     * The team was available to help when asked, and every response records whether help was needed.  
     * Consenting participants recorded their screens; 10 recordings were submitted.  
   * **Activities/tasks:**  
     * **Contributor:** create and submit a complete post (details, caption, media, album, schedule), respond to schedule warnings, find and reuse a library asset, and try the AI features if they appeared.  
     * **Moderator:** find a queued submission, perform a terminal review action (approve or request revision), and locate the post on the calendar.  
     * **Administrator:** set up an institution and invite users, confirm a post's scheduling or publication status, and resolve a failed publication if one was present.  
   * **How data was collected:**  
     * A nine-section Google Form with role and AI branching, covering task results, time on task, Likert grids, post-task satisfaction, SUS, and four open-ended questions.  
     * Screen recordings for triangulation.  
     * Inspection of the system's own logic, where a finding needed confirmation.

## **Section 3 — MVP Validation Findings**

**Summary of measured results vs. proposal targets**

| Measure | Result | Proposal target | Status |
| ----- | ----- | ----- | ----- |
| Contributor first-attempt submission (C1) | 3 without help, 4 with help, **1 unable** (n \= 8\) | ≥ 95% first-attempt form completion (GO1) | Not met |
| All roles: completed at all / completed **without help** | 15 / 16 (93.8%) / **6 / 16 (37.5%)** | Independent use "without coordinator intervention" (SO 1.3) | Gap |
| SUS, complete responses (n \= 15; one incomplete excluded) | Mean **61.8**, median 60.0 | ≥ 70, n ≥ 10, three roles (GO2, RQ3) | Not met |
| SUS excluding 4 straight-lined responses (n \= 11\) | Mean **66.1** | ≥ 70 | Not met |
| SUS by role | Contributor 61.9 (n=8) · Moderator 58.1 (n=4) · Administrator 66.7 (n=3) | ≥ 70 | All below |
| Overall satisfied (PS4) | 15 / 16 Agree or Strongly agree | — | High |
| Encountered any AI feature (AI1) | 6 / 16 (Contributors: 4 / 8\) | — | Low exposure |
| AI caption accepted / accepted with edits (AI2) | 4 / 5 valid caption exposures | ≥ 60% (GO3c) | Promising,  too small |
| AI caption quality (AI3) | Mean 4.0 (n \= 4\) | ≥ 4.0 across ≥ 20 submissions (GO3d) | Not yet measurable |
| First recommended media relevant (AI4) | 4 / 5 | — | Promising |

### **Finding \#1 — The readiness percentage made a complete, valid post look unfinished**

1. **What did we learn from the stakeholders?**

   One Contributor could not complete the submission task, and was the only participant unable to finish any task. On a tablet, they reported: *"It only stopped at 90% when I input information on every blank needed and cannot submit… Remove percentage needed and make it as guide but not to comply for submission."*

   

   We traced the cause in the system's logic:

   

* The composer computes one readiness score: **75% × (share of the 7 required checks passed) \+ 25% × (share of the 5 optional recommendations passed)**.  
* A post with every required field complete but only 3 of 5 optional tips (for example, no hashtags or no template) scores **exactly 90%**, and it is **already submittable**.  
* Because the score runs to 100, users read anything below 100 as "not done".  
    
  The problem is in the metric's design, not in the code: one number answers two different questions, "Can I submit?" and "How good is this post?".

2. **Evidence:**

   * Validation responses: C1 \= Unable to complete, plus F1, F2, and F3 comments.  
   * The participant's screen recording.  
   * Inspection of the readiness calculation in the composer.

3. **Stakeholder type:** End User (Contributor).

4. **SMART objective / measurable outcome affected:**

   * **GO1:** ≥95% form completion rate on the first submission attempt.  
   * **SO 1.7:** verified ≥95% form completion on submitted records.  
   * **RQ2:** content completeness rate vs. baseline.

5. **Measurable outcome that could be improved:** Effectiveness (first-attempt completion), Usability, User satisfaction.

6. **How the finding affects the objective:**

   * GO1 counts completion on the *first* attempt. A Contributor who abandons a valid draft at 90% never produces a submitted record, so the event goes uncovered.  
   * This is exactly the "events that receive no coverage at all" problem the proposal set out to solve.  
   * One failure in eight Contributor sessions (12.5%) already makes ≥95% unreachable for that role.  
   * The cause is the readiness model itself, so better wording around it would not remove it.

### **Finding \#2 — Users were satisfied but not yet independent; the system is learnable, not self-explanatory**

1. **What did we learn from the stakeholders?**  
   Participants liked DASIGConnect and endorsed its workflow:  
* 15 of 16 were satisfied overall.  
* Several said the workflow "should be retained".  
  But they could not yet use it without help:  
* **Only 6 of 16 sessions (37.5%) were completed without help.** By role: Contributors 3/8, Moderators 2/5, Administrators 1/3.  
* SUS averaged **61.8**, below the target of 70\. Every role scored below 70, even after removing straight-lined responses.  
* The weakest SUS items were about learning burden: **8 of 15** agreed they *"needed to learn a lot of things before I could get going"* (SUS10), and 5 of 15 agreed they *"would need the support of a technical person"* (SUS4).  
  Open-ended comments matched the numbers:  
* *"Just needed confirmation on what to do before doing anything."*  
* *"At first, I did not know what DASIGConnect really is, its function… I had a hard time understanding each function."*  
* *"I did ask for help more often than I thought I would."*  
2. **Evidence:**  
* Task results C1, M1, and AD1.  
* SUS1–SUS10 and PS1–PS4.  
* Open-ended responses F1 and F4.  
* Screen recordings.  
3. **Stakeholder type:** End Users and Decision Makers, all three roles.  
4. **SMART objective / measurable outcome affected**  
* **GO2 / RQ3:** SUS ≥70 from ≥10 pilot users across Contributors, Moderators, and Administrators.  
* **SO 1.3:** independent content submission from ≥3 member institutions *without coordinator intervention*.  
* **GO1:** first-attempt completion.  
5. **Measurable outcome that could be improved:** Usability (SUS), Effectiveness (independent task success), Efficiency (fewer help requests).  
6. **How the finding affects the objective:**  
   * SO 1.3 explicitly requires submission *without coordinator intervention*, but most sessions needed the team's help. In the pilot, no developer will be on hand, so many of these sessions could become abandoned or incomplete submissions. That would lower GO1 and SO 1.7 and reduce posting frequency (RQ4, SO 3.4's ≥4 posts per month per institution).  
   * The SUS shortfall holds even after removing straight-lined responses, so it is real and not a scoring artefact.  
   * Because satisfaction is high, the gap is not the workflow itself. It is how clearly the system tells each user where they stand and what to do next.

### **Finding \#3 — AI caption suggestions only appear on request, so most users never saw one**

1. **What did we learn from the stakeholders?**  
   Only **6 of 16** participants, and only **4 of 8 Contributors**, encountered any AI feature. Among those who did, results were encouraging:  
* 4 of 5 valid caption exposures were accepted (3 unchanged, 1 after editing).  
* Mean quality was 4.0.  
* 4 of 5 rated the first recommended media asset relevant.  
  Perceived usefulness was mixed: only 3 of 6 agreed the AI helped them work faster (AI5):

  We traced why exposure was low. In the MVP, a caption is generated **only when the Contributor clicks "Suggest Caption"**. Nothing is generated when an image is uploaded. This differs from the proposal's own design: *"when a contributor uploads an image during submission, the system will use a vision-capable language model to analyze the image and generate suggested captions."*

2. **Evidence:**  
   * Validation responses AI1–AI8.  
   * The open-ended comment naming "the A.I. recommendations" as the hardest part.  
   * Inspection of the caption feature, which only runs when the button is clicked.  
   * AI caption interaction logs, which exist and can confirm these figures.

3. **Stakeholder type:** End Users (Contributors, one Moderator), Decision Makers.  
4. **SMART objective / measurable outcome affected:**  
   * **GO3(c):** ≥60% acceptance or accept-with-edits on AI caption suggestions *generated for ≥90% of uploaded images*.  
   * **GO3(d):** average quality ≥4.0/5 across ≥20 evaluation submissions.  
   * **SO 1.6.**  
   * **RQ5:** reduction in caption drafting time, and quality ratings.  
5. **Measurable outcome that could be improved:** Effectiveness (AI coverage and acceptance), Efficiency (drafting time), Accuracy (caption relevance).  
6. **How the finding affects the objective:**  
   * **Coverage:** GO3(c) requires suggestions to be generated for ≥90% of uploaded images. With generation only on request, coverage equals the share of users who notice and click the button. In this validation that was well under half of Contributors, so the coverage target cannot be met by design.  
   * **Sample size:** the acceptance and quality results look promising, but they come from 4–5 people. GO3(d) needs ≥20 evaluation submissions, which the current design would struggle to produce.  
   * **Drafting time (RQ5):** this is not measured anywhere yet, so the research question cannot currently be answered.

### **Finding \#4 — Review and other key actions were hard to find on phones, where Moderators receive their alerts**

1. **What did we learn from the stakeholders?**  
   Half of all sessions (8 of 16\) were on phones or tablets. Participants reported problems with key actions there:  
* A Moderator on a phone *"wasn't able to locate the review button right away, as there was no visible button"* and said *"using a phone is often more convenient, so having full functionality on mobile would be helpful for us… especially when making up-to-date posts on the page."*  
* A second mobile Moderator named *"navigating through the page"* as the hardest part.  
* An Administrator asked for the mobile view to be fixed.  
  Moderators also had the lowest SUS of the three roles (58.1).  
2. **Evidence:**  
* Device (P7) cross-referenced with task results.  
* Open-ended responses F1–F4.  
* SUS by role.  
* Screen recordings.  
3. **Stakeholder type:** End Users (Moderators), Decision Maker (Administrator).  
4. **SMART objective / measurable outcome affected:**  
   * **GO2(a) / SO 2.4:** 100% Moderator-reviewed approval before publication.  
   * **GO2(b) / SO 3.3:** notifications delivered within 5 minutes in ≥95% of cases.  
   * **GO3(b) / SO 1.9 / RQ6:** expedited same-day publication and its approval turnaround.  
   * **SO 1.10:** Moderator access to the approval queue and publication calendar.  
5. **Measurable outcome that could be improved:** Effectiveness (review completion on mobile), Efficiency (approval turnaround), Usability.  
6. **How the finding affects the objective:**  
   * DASIGConnect sends Moderators submission and urgent Fast-Track alerts through Messenger, which they mostly read on their phones.  
   * Delivering an alert within 5 minutes (SO 3.3) doesn't help if the Moderator can't act on it from that phone. The review then waits until the Moderator reaches a desktop.  
   * Time-sensitive events are hit hardest. The expedited pathway (SO 1.9) exists to get same-day coverage, and RQ6 measures its approval turnaround.  
   * Mobile support was treated as layout polish in the MVP. The evidence shows it is a condition for the review workflow to meet its timing goals.

### **Finding \#5 — Manual, form-by-form invitations do not scale to network-wide provisioning**

1. **What did we learn from the stakeholders?**  
   * An Administrator called *"sending invites"* the most difficult part of the task. They noted that *"manually adding emails to invite to be moderators and contributors would cause much time for administrator's job,"* and suggested letting members request access, with the Administrator verifying that they belong to DASIG.  
   * The current invite form takes a typed or pasted list of up to 15 emails, all sharing one role and one institution. To provision a new institution's Contributors and Moderators, an Administrator has to repeat the form for each role and each institution.  
   * Problems with an email (duplicate, existing account, deactivated account, Administrator cap) only show up *after* sending, one at a time. There is no preview step, and no single record of the whole batch.  
   * In the test, provisioning was quick: a median of 2 minutes for one institution and a few users. But the effort grows with every person and member institution added.  
2. **Evidence:**  
   * Validation responses AD1 and AD2 (the Administrator's task result and time), and F1 and F2 (the Administrator's answers on the most difficult part and the most-wanted change).  
   * Inspection of the current invitation flow:  
     * **Frontend:** the browser loops over the email list and sends one invitation request per email.  
     * **Backend:** the server accepts a single invitation per request. It has no batch endpoint and no batch-level audit record.  
3. **Stakeholder type:** Decision Maker (Administrator).  
4. **SMART objective / measurable outcome affected:**  
   * **SO 1.3:** provisioning for ≥3 member institutions.  
   * **GO1:** at least two operational institutions with three roles.  
   * **SO 1.10:** Moderator provisioning.  
5. **Measurable outcome that could be improved:** Efficiency (Administrator time per provisioned user), Productivity.  
6. **How the finding affects the objective:**  
   * Provisioning comes before every other objective: an institution cannot post until its people have accounts. Slow onboarding therefore delays when posting frequency and completeness (SO 3.4, RQ4) can start being measured for each institution.  
   * The respondent's self-registration idea conflicts with SO 1.4 (zero unauthorized access). The refactoring keeps admission invitation-only and controlled by Administrators and Moderators, while removing the repetitive, form-by-form cost.

## **Section 4 — Potential Refactoring Priorities**

### **Refactoring Priority \#1 — Separate the submission gate from the post-quality score**

1. **Validation Finding:** Finding \#1. It also supports Finding \#2.  
2. **What needs to be improved or changed:** Replace the single blended readiness percentage with two distinct signals: whether the post can be submitted, and optional tips to improve it.  
3. **Type of refactoring:** Functional requirement · Data/input/output · Usability.  
4. **Current MVP approach:**  
   - **Score:** one 0–100 score blends 7 required checks (75%) with 5 optional recommendations (25%).  
   - **When submission is possible:** once all required checks pass, at any score from 75 to 100\. The only cue is a small "Ready to submit" label beside the number.  
5. **Proposed refactored approach:**  
   - **Submission gate:** a binary status, e.g. *"Required: 7 of 7 ✓ — Ready to submit"*. It is the only thing that can block submission. While incomplete, it names the next missing item and jumps to it.  
   - **Post quality tips:** shown separately and clearly optional (*"Tips: 3 of 5 applied"*), never as a percentage that implies a pass mark.  
   - **Submit button:** when the gate passes, Submit becomes the primary, always-visible action on every screen size.  
   - **Requirements update:** in the SRS (UC-1.5, UC-1.9), "readiness" is redefined as the required-check gate only. Recommendations become non-blocking advisories.  
6. **Why this change is necessary:** The only task failure in the entire validation came from a user reading 90% as "not allowed to submit". GO1 measures first-attempt completion, so every valid draft abandoned this way counts directly against it.  
7. **Expected improvement:** Higher first-attempt submission completion for Contributors, and fewer abandoned drafts that were already complete.  
8. **How the improvement will be measured:**  
   - **Task completion rate (C1)** in re-validation, targeting ≥95%.  
   - **From system logs:** the share of drafts that passed all required checks but were not submitted within 24 hours.  
   - **Time to submit:** the median time from "all required checks passed" to submission.  
9. **Priority:** 🔴 **High** — essential to GO1 and SO 1.7.

### **Refactoring Priority \#2 — Design for first-time independence: next-action guidance and plain-language status, with first-attempt completion measured by the system**

1. **Validation Finding:** Finding \#2.  
2. **What needs to be improved or changed:**  
   - Guidance should respond to where the user currently is, instead of being reference material they must go looking for.  
   - First-attempt success should become something the system measures, not just something users self-report.  
3. **Type of refactoring:** Non-functional requirement (learnability) · System process/workflow · Usability.  
4. **Current MVP approach:**  
   - **Screens and tours:** role-based screens with optional, view-only guided tours.  
   - **Statuses:** labels use internal workflow terms such as "needs revision", "missed review", and "Fast-Track", without saying what happens next or who acts.  
   - **Measurement:** first-attempt success is known only from the questionnaire.  
5. **Proposed refactored approach:**  
   - **Next action on each role's home screen:** the single most important pending task leads the dashboard.  
     - Contributor: *"Finish your draft — 1 required item left."*  
     - Moderator: *"3 submissions awaiting review — Review next."*  
     - Administrator: *"1 failed publication needs action."*  
   - **Plain-language status:** every submission status states what it means and who acts next, e.g. *"Needs revision — the Moderator asked for changes. Edit and resubmit."* The same wording is used in notifications.  
   - **Instrumentation:** the system records the start and finish of each role's core task, so first-attempt completion and time-on-task come from logs, as the proposal's Validation Approach requires.  
   - **New SRS non-functional requirement (learnability):** first-time users complete their role's core task without assistance in ≥95% of attempts, confirmed by SUS ≥70 after the pilot.  
6. **Why this change is necessary:**  
   - Participants asked us to keep the workflow. What they lacked was confidence about what to do next.  
   - GO2's SUS target and SO 1.3's "without coordinator intervention" both depend on closing this gap.  
   - Fixing screens one at a time would not close it, because the gap is how the whole system communicates state.  
7. **Expected improvement:**  
   - Independent task success rises from 37.5% toward ≥95%.  
   - SUS rises from 61.8 toward ≥70, particularly the learning-burden items (SUS4, SUS10).  
8. **How the improvement will be measured:**  
   - Logged first-attempt completion rate per role.  
   - Help requests per session, coded from screen recordings.  
   - SUS after the pilot, aggregate and per role (n ≥ 10).  
   - The distribution of responses on SUS4 and SUS10.  
9. **Priority:** 🔴 **High** — essential to GO2 (SUS ≥70) and SO 1.3.

### **Refactoring Priority \#3 — Generate AI caption suggestions when images are uploaded, as the proposal intended, and measure GO3 from system logs**

1. **Validation Finding:** Finding \#3.  
2. **What needs to be improved or changed:**  
   - **When suggestions are generated:** automatically on upload, instead of only on request.  
   - **How AI outcomes are measured:** from system logs over a properly sized sample, instead of from a questionnaire.  
3. **Type of refactoring:** Functional requirement · System process/workflow · Algorithm/method (evaluation).  
4. **Current MVP approach:**  
   - **Caption suggestions:** generated only when the Contributor clicks "Suggest Caption".  
   - **Media suggestions:** sit in a separate AI Suggestions tab of the media picker.  
   - **Measurement:** acceptance and quality come from survey recall (n \= 4–5). Caption drafting time is not measured.  
5. **Proposed refactored approach:**  
   - **Generate on upload:** once at least one image and an event title exist, the system generates a caption suggestion in the background.  
   - **Offer inline:** the suggestion appears in the caption field area as *"AI draft ready — Use / Edit / Dismiss"*. It is never inserted automatically, which keeps the proposal's "non-intrusive, contributor retains editorial control" principle.  
   - **Media suggestions first:** suggested media is shown by default in the media step once there is enough event context.  
   - **Logged outcomes:** for every generated suggestion, the system records whether it was generated, used, used after editing, or dismissed, plus a one-click quality rating (1–5) at the moment of accept or dismiss, and the time from opening the caption step to saving a caption.  
   - **SPMP evaluation protocol:** GO3(c) and GO3(d) are computed from these logs over ≥20 pilot submissions.  
   - **Cost and limits:** existing per-user AI rate limits continue to apply. If generation fails, the composer falls back to manual entry without blocking submission.  
6. **Why this change is necessary:**  
   - GO3(c) is defined over *uploaded images* (≥90% must receive a suggestion). An on-request design cannot meet that, and in validation fewer than half of Contributors ever triggered it.  
   - Users who did see a suggestion mostly kept it (4 of 5), so the barrier is reach, not quality.  
   - RQ5 asks whether AI reduces drafting time, and nothing measures that today.  
7. **Expected improvement:**  
   - Suggestion coverage rises to ≥90% of uploaded images.  
   - Acceptance and accept-with-edits reaches ≥60%.  
   - Average quality reaches ≥4.0/5 over ≥20 submissions.  
   - Caption drafting time drops.  
8. **How the improvement will be measured:**  
   - **Coverage:** suggestions generated ÷ submissions with images.  
   - **Acceptance:** suggestions accepted (with or without edits) ÷ suggestions generated.  
   - **Quality:** mean rating over ≥20 submissions.  
   - **Drafting time:** median time to caption, compared between submissions that used AI and those that didn't.  
   - **Reliability:** generation success rate and response time.  
9. **Priority:** 🔴 **High** — essential to GO3(c) and (d) and to answering RQ5.

### **Refactoring Priority \#4 — Make mobile operability a requirement for every role's critical actions**

1. **Validation Finding:** Finding \#4.  
2. **What needs to be improved or changed:** Mobile support should become an explicit, testable requirement that every role's core action can be completed on a phone, instead of a best-effort responsive layout.  
3. **Type of refactoring:** Non-functional requirement · Usability · System process/workflow (notification-to-action).  
4. **Current MVP approach:**  
   - **Design:** desktop-first, with responsive styling added afterwards.  
   - **Small screens:** some actions sat in side panels or menus that were not visible.  
   - **Since validation:** several mobile layout fixes have shipped, but no requirement or test guarantees mobile parity, so it can regress.  
5. **Proposed refactored approach:**  
   - **New SRS non-functional requirement:** at 360-px phone width, each role-critical action is reachable on its screen without horizontal scrolling. These actions are submit, approve, request revision, reject, reschedule, resolve a failed publication, and accept an invitation.  
   - **SDD design rule:** each task screen has one persistent primary-action bar on mobile.  
   - **Notification deep links:** Messenger and email alerts open the exact submission, already in review mode.  
   - **Acceptance tests:** each role's core task is tested on phone and tablet viewports.  
6. **Why this change is necessary:** Half of real use was on phones and tablets, and Messenger alerts naturally send Moderators to their phones. Without mobile parity, fast notifications (SO 3.3) do not lead to fast reviews, and same-day expedited coverage (SO 1.9) slips.  
7. **Expected improvement:** Higher task completion on mobile, shorter approval turnaround (especially for expedited submissions), and a higher Moderator SUS.  
8. **How the improvement will be measured:**  
   - Task completion rate by device in re-validation.  
   - Median time from submission to review decision, taken from system timestamps.  
   - Expedited-pathway approval turnaround (RQ6).  
   - Moderator SUS.  
9. **Priority:** 🟠 **Medium** — a significant improvement that supports SO 2.4, SO 3.3, and SO 1.9 rather than being a stated objective itself.

### **Refactoring Priority \#5 — Bulk invitations with preview and batch audit, still controlled by Administrators and Moderators**

1. **Validation Finding:** Finding \#5.  
2. **What needs to be improved or changed:** Make it possible to provision many users across roles and institutions in one validated, auditable step, without opening self-registration.  
3. **Type of refactoring:** Functional requirement · System process/workflow · Data/input/output.  
4. **Current MVP approach:**  
   - **Invite form:** accepts up to 15 typed or pasted emails. Every email in one submission shares a single role and a single institution.  
   - **Sending:** the browser loops over the list and sends one invitation request per email.  
   - **Checks:** the server checks each invitation individually (existing active account, deactivated account, the 3-Administrator cap) and writes one audit record per invitation.  
   - **Not supported:** structured input (CSV), a preview or confirm step before sending, a batch endpoint, and a batch-level audit record. Problems surface only after each email has been sent.  
5. **Proposed refactored approach:**  
   - **CSV / structured input:** upload or paste rows of `email, role, institution`, so one batch can cover several roles and institutions.  
   - **Preview and confirm:** before anything is sent, every row is parsed and validated, and each row's status is shown:  
     - Duplicate within the batch.  
     - Invalid email.  
     - Unknown or inactive institution.  
     - Active account already exists.  
     - Deactivated account (marked "use Reactivation" and excluded from the batch).  
     - Pending invite that will be superseded.  
     - Would exceed the 3-Administrator cap.  
   - **Confirm valid rows only:** the Administrator confirms the valid rows in one action.  
   - **Backend batch endpoint:** accepts the batch items, applies the same validation on the server (the client preview is only advisory), creates the invitations, and returns a per-row result.  
   - **Audit:** one batch-summary audit record (actor, counts, outcome), plus the existing per-invitation records linked to the batch.  
   - **Permissions unchanged:** Moderators may batch-invite Contributors only. Administrators may batch-invite every role.  
   - **Rejected alternative:** an open "request access" form, recorded as considered and rejected for this version because it conflicts with SO 1.4. A future version could add it behind an approval queue.  
6. **Why this change is necessary:**   
- Onboarding member institutions comes before every content objective (SO 1.3, SO 3.4).  
- With the current form, effort grows with every role-and-institution combination, and errors are only discovered after sending.  
- A validated batch flow removes repeated form submissions and failed sends, while admission stays invitation-only.  
7. **Expected improvement:** Less Administrator time per provisioned user, fewer failed or invalid invitations, and a shorter time to onboard an institution.  
8. **How the improvement will be measured:**  
   - AD2 time-on-task in re-validation, using a 10–20 user list across at least two roles and institutions.  
   - Share of invitations that fail after sending.  
   - System timestamps from batch creation to the last invitation sent.  
9. **Priority:** 🟢 **Low–Medium** — useful for scaling, but supported by a single respondent and not blocking any SMART objective at pilot size.

## **Section 5 — Refactoring Summary Matrix**

| \# | Validation Finding | Evidence | SMART Objective / Outcome Affected | Proposed Refactoring | Expected Improvement | Priority |
| :---- | :---- | :---- | :---- | :---- | :---- | :---- |
| 1 | Readiness score reached 90% on a complete, valid post; the Contributor thought submission was blocked and gave up | C1 \= Unable (only failure in the study); F1–F3; recording; readiness formula (75% required \+ 25% optional) | GO1, SO 1.7, RQ2 | Split into a required-check submission gate and separate optional quality tips; promote Submit when the gate passes | First-attempt submission completion ≥ 95%; fewer abandoned complete drafts | 🔴 High |
| 2 | Users satisfied but not independent | 6/16 completed without help (37.5%); SUS 61.8 (66.1 without straight-liners); 8/15 agree "needed to learn a lot" | GO2 (SUS ≥ 70), SO 1.3, GO1 | Next-action role home screens; plain-language statuses; learnability NFR; logged first-attempt completion | Independent success toward 95%; SUS ≥ 70 | 🔴 High |
| 3 | AI captions only generated on request; most users never saw one, though most who did accepted it | AI1 6/16; AI2 4/5 accepted; AI3 mean 4.0 (n=4); caption only generated on button click | GO3(c)(d), SO 1.6, RQ5 | Generate suggestions on image upload (offered, not inserted); suggested media shown first; log-based GO3 measurement over ≥ 20 submissions; measure drafting time | ≥ 90% coverage; ≥ 60% acceptance; quality ≥ 4.0; shorter drafting time | 🔴 High |
| 4 | Review and other key actions hard to find on phones, where Moderators receive alerts | 8/16 sessions on phone or tablet; Moderator could not find review button; Moderator SUS 58.1 | GO2(a)(b), SO 2.4, SO 3.3, SO 1.9, RQ6 | Mobile-parity NFR (360 px); persistent primary-action bar; notification deep links; device acceptance tests | Mobile completion; shorter approval turnaround, especially expedited | 🟠 Medium |
| 5 | Manual, form-by-form invitations won't scale | Administrator AD1/AD2/F1/F2; invite form limited to 15 emails with one role and one institution, sent one request per email, no preview or batch audit | SO 1.3, SO 1.10, GO1 | CSV bulk invitation (email, role, institution) with preview and confirm, a server-side batch endpoint, and batch audit; open self-registration rejected for security | Less Administrator time per provisioned user; fewer failed invitations | 🟢 Low–Med |

## Section 6 — Items That Are NOT Refactoring Priorities

| Item | Identified? | Action |
| :---- | :---- | :---- |
| Bugs/errors | **Yes** — respondents asked to "fix the existing bugs" raised in the debrief meeting | Fix during implementation and testing. Several mobile layout defects were already fixed after validation. |
| Minor UI/UX issues | **Yes** — "improve the visual elements"; top navigation with icons | Improve as appropriate. The hidden review button is not treated as minor, because it blocks a core action (Priority \#4). |
| Missing original requirement | **Yes** — the proposal says AI captions are generated when images are uploaded, but the MVP generates them only on request | Implement as part of Priority \#3 |
| Missing essential functional requirement | **Yes** — a submission gate separate from the quality score; validated bulk provisioning | Incorporated as Priorities \#1 and \#5 |
| Missing non-functional requirement | **Yes** — learnability and mobile operability were never specified | Incorporated as Priorities \#2 and \#4 |
| New feature unrelated to SMART objectives | **Yes** — (a) open self-registration without approval; (b) "multiple and different fonts in one post" | (a) Not adopted, because it conflicts with SO 1.4 (zero unauthorized access). Recorded as a deferred alternative; Priority \#5 keeps admission invitation-only. (b) Already supported: the text-styling tool (SO 1.6) styles any selected text, so several styles can be mixed in one caption. This is a discoverability issue covered by Priority \#2. |
| Improvement to measurable outcome | **Yes** | Priorities \#1–\#4 |
| Change in system approach/design | **Yes** — readiness model; AI suggestions generated on upload instead of on request | Priorities \#1 and \#3 |
| Improvement in accuracy/efficiency/effectiveness/performance | **Yes** | Priorities \#1–\#5 |

**Also not treated as refactoring:** one participant cited slow internet as the main difficulty. This is already Limitation \#1 in the proposal (reliable connectivity is required) and is not a system defect.

**Validation limitations, disclosed per the proposal and the framework:**

- **Not the pilot:** these were short, mostly first-time sessions, not the one-month pilot. The controlling SUS ≥70 test (GO2, RQ3) will be repeated after the pilot, and the figures above are directional.  
- **Straight-lined SUS:** 4 SUS responses gave every item the same rating, which mechanically scores 50\. Results are reported both with and without them.  
- **Incomplete SUS:** 1 response was incomplete (9 of 10 items) and was excluded.  
- **Repeat participant:** one participant tested two roles.  
- **Self-reported times:** several media-search times were entered as "0". Efficiency claims will rely on logs and recordings.

## Section 7 — Final Team Reflection

1. **What is the single most important thing your team learned from the MVP validation?**

User satisfaction is not the same as independent task completion.

- **What went well:** participants liked DASIGConnect and endorsed its workflow. 15 of 16 were satisfied, and several asked us to keep the workflow as it is.  
- **What fell short:** only 37.5% completed their task without help, and SUS (61.8) fell short of the proposal's target of 70\.  
    
  We built the MVP on the assumption that a correct, complete workflow would be enough. Validation showed that the system must also clearly tell users where they stand and what to do next. Where it gave a misleading signal, the 90% readiness score, a user with a valid post gave up.  
    
  We also learned that a feature can perform well and still miss its objective if users don't reach it. Our AI caption suggestions were accepted by 4 of 5 people who saw them, but most users never saw them.

2. **What is the most important change your team should make based on this learning?**

   Redesign how the system communicates state and offers help, without changing the core workflow users endorsed:

- A clear required-check submission gate instead of a blended percentage.  
- A next action on every role's home screen, and plain-language statuses.  
- Primary actions available on phones.  
- AI caption suggestions generated when images are uploaded, as the proposal originally specified, instead of hidden behind a button.

3. **How will this change help your project achieve its SMART objectives more effectively?**  
   - **GO1 / SO 1.7 (≥95% first-attempt completion):** removing the misleading readiness signal, and showing every user their next action, address the specific causes of failed and assisted tasks we observed.  
   - **GO2 (SUS ≥70) and SO 1.3 ("without coordinator intervention"):** the same changes target the learning-burden items that pulled SUS down.  
   - **GO3 (≥90% coverage, ≥60% acceptance, quality ≥4.0):** generating suggestions on upload makes the coverage target reachable by design and produces enough interactions to measure acceptance and quality validly.  
   - **Measurement:** logging first-attempt completion, AI interactions, and drafting time lets us verify all three general objectives, and answer RQ3 and RQ5, from system evidence rather than recall, as our Validation Approach requires.

4. **What requirements and/or design documents will need to be updated as a result?**

   - ☑ **SRS:**  
     - UC-1.5 / UC-1.9: readiness redefined as a required-check gate, with separate quality tips.  
     - UC-1.6: suggestion generated on upload and offered inline.  
     - UC-1.7: AI-suggested media shown first.  
     - UC-1.1 / UC-1.3 / UC-1.10: CSV bulk invitation with preview and confirm, server-side batch validation, and batch audit.  
     - New non-functional requirements: learnability, and mobile operability at 360 px.  
     - Status-wording requirements for notifications and submission statuses.  
   - ☑ **SDD:**  
     - Composer readiness model and components.  
     - Role home "next action" design.  
     - Mobile primary-action bar and notification deep-link routing.  
     - Background caption generation on upload.  
     - Batch-invitation endpoint, CSV parsing and preview validation, and the batch-summary audit record.  
     - Task-event and AI-interaction logging schema.  
   - ☑ **SPMP:**  
     - Sprints for Priorities \#1–\#5.  
     - A re-validation round after refactoring.  
     - A one-month pilot with log-based measurement of GO1, GO3, and RQ5.  
   - ☑ **Other:** MVP Validation Framework v5.0, to add the log-based measures and the rule for excluding straight-lined SUS responses.

## **Final Declaration**

**We certify that the findings and proposed refactoring priorities submitted in this form are based on actual MVP validation feedback gathered from our identified stakeholders/users. We understand that the proposed refactoring must be justified in relation to our project's SMART objectives and measurable outcomes.**

**Team Lead: Richemmae V. Bigno**

**Date: October 12, 2026**  
