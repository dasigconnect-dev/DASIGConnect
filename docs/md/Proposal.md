# **Project Proposal Guide (Weeks 7–8)**

This worksheet guides the team in developing the Capstone/Software Engineering Project Proposal. It integrates the refined problem statement, findings from the Review of Related Literature (RRL), and the proposed solution concept.

## **Team Information**

Project Title: DASIGConnect

Project Short Description (\<20 words): A centralized platform for coordinating, validating, and scheduling DASIG social media content across multiple member institutions.

Team Code: 2526-sem2-it332-38

Members:

1. Richemmae V. Bigno

2. Jay Lord C. Bayonas

3. Chris Daniel P. Cabatana

4. Mark Anton L. Camoro

5. Lerah A. Caones

   

## **PART 1: Introduction (Approx. 300 words)**

The management of organizational social media presence has become a critical operational function in academic and research institutions. Effective and timely communication through social media platforms directly influences stakeholder awareness, institutional visibility, and community engagement (Kaplan & Haenlein, 2010). For multi-organizational networks such as the DOST Acadême–Science and Innovation Group (DASIG), which consolidates participation from multiple Higher Education Institutions (HEIs) including the Cebu Institute of Technology–University (CIT-U), Silliman University, and other member schools under DOST Region 7, the challenge of coordinating and publishing content across diverse contributors is particularly pronounced.

Preliminary observations of the DASIG Facebook page reveal recurring patterns of delayed event coverage, incomplete posts, and irregular publishing activity. Events held by member institutions are frequently reported several days after their occurrence, and some activities receive no coverage at all due to missing media assets or unclear submission responsibilities. This irregularity has resulted in reduced audience engagement and inadequate representation of the organizations’ activities on the platform. The root cause of these issues can be traced not to a lack of content, but to the absence of a structured workflow for collecting, validating, and scheduling content from multiple independent sources.

Existing literature consistently demonstrates that structured content workflows and scheduling tools significantly improve the timeliness and consistency of organizational social media outputs (Turban et al., 2018). Studies on collaborative digital publishing further indicate that approval-based moderation mechanisms reduce errors and improve content quality in multi-contributor environments (O’Reilly, 2007). Additionally, research on social media management in academic organizations confirms that irregular posting patterns negatively affect institutional credibility and audience retention (Aral et al., 2013).

Recent advancements in artificial intelligence, particularly in image recognition and natural language generation, offer practical opportunities to further improve content workflow efficiency. AI-assisted caption generation based on uploaded images, along with intelligent content suggestions drawn from the media repository, can reduce the manual burden on contributors and improve the consistency of post quality without requiring significant technical expertise from end users (Radford et al., 2021; Li et al., 2022). 

Despite these established findings, no documented system specifically addresses the multi-institution content coordination challenge faced by academic consortia such as DASIG, particularly one that integrates AI-assisted tools to support contributors throughout the content preparation process.

## **PART 2: Objectives**

## **General Objectives**

This project aims to achieve the following general objectives, structured by key system features, within the duration of two academic semesters:

**1\. Foundation, Access Control, and Content Submission.** To increase content submission completeness to a ≥95% form completion rate on first submission attempt, through a secure, multi-organization submission portal with role-based access control supporting at least three (3) DASIG member institutions, to be delivered by the end of Module 1\.

**2\. Validation Workflow, Notification System, and Analytics.** To eliminate unapproved content publication and reduce submission-status notification delay, through a content validation, notification, and resource management subsystem that (a) enforces a 100% administrator-reviewed approval step before any post is published, (b) reduces notification delivery time to within 5 minutes of state change in ≥95% of cases, and (c) improves media asset accessibility through a searchable library indexed by institution and event, achieving a System Usability Scale (SUS) score of ≥70 from at least 10 pilot users by the end of Module 2\.

**3\. Scheduling, Automated Publishing, and AI-Assisted Content Support.** To improve posting timeliness and reduce caption drafting effort, through an automated scheduling, publishing, and AI-assisted content subsystem that (a) increases scheduled post publish success rate to ≥95% within ±5 minutes of the assigned time, (b) achieves a ≥60% contributor acceptance or accept-with-edits rate on AI caption suggestions generated for ≥90% of uploaded images, and (c) attains an average AI suggestion quality rating of ≥4.0 / 5.0 across at least 20 evaluation submissions, by the end of Module 3\.

## **Specific Objectives**

To achieve the general objectives, the project will accomplish the following feature-specific tasks, which serve as sub-objectives:

**1\. For General Objective 1: Foundation, Access Control, and Content Submission**

1.1. To enable independent content submission from at least three distinct DASIG member institutions without coordinator intervention, by defining and implementing distinct access roles for Contributor, Validator, and Administrator. 

1.2. To ensure institution-level data isolation and eliminate unauthorized cross-institution access, by implementing a secure authentication and onboarding mechanism that provisions isolated workspaces per institution, with role-based routing to either the institutional workspace or the network admin console upon login. 

1.3. Develop the content submission form with server-enforced validation on the following mandatory fields: event title, event date, description, caption, at least one tag, and at least one media upload (photo or video). The form shall reject submissions missing any mandatory field with field-level error messaging, achieving a verified ≥95% form completion rate on submitted records during pilot testing. 

**2\. For General Objective 2: Validation Workflow, Notification System, and Analytics**

2.1. Implement a validation interface that eliminates unapproved or erroneous posts by enforcing a 100% administrator-reviewed approval step, with the ability to approve, request revisions with remarks, or reject submissions before publication.

2.2. To improve media asset retrieval speed to ≤2 seconds and increase asset reuse across submissions, by building a centralized digital media repository that (a) stores uploaded photos and videos with per-institution isolation enforced via row-level scoping, (b) supports search by filename, tag, and uploader for libraries up to 1,000 assets, and (c) enables asset reuse via a one-click "Use in new post" action, with reuse tracked in submission records. 

2.3. To reduce contributor response time and minimize workflow delays, by ensuring email notifications reach contributors within 5 minutes of any submission status change (Pending → Approved, Needs Revision, or Rejected), targeting a SUS score of at least 70\.

2.4. To reduce in-app notification latency to within 30 seconds and improve revision transparency for contributors, by implementing (a) a real-time notification badge triggered within 30 seconds of any submission state change, and (b) a remarks field on the validation interface that captures and surfaces administrator feedback during the "Request Revision" action. Average revision round-trips per submission shall be observed and reported during the pilot phase as a system performance indicator.

2.5. Create a social media analytics dashboard that improves administrator visibility by tracking posting frequency (posts/month), submission-to-publish duration (days), and content completeness rate (%), tar	geting a minimum of 4 posts per month and a completeness rate of at least 95%.

**3\. For General Objective 3: Scheduling, Automated Publishing, and AI-Assisted Content Support**

3.1. To increase scheduling efficiency and eliminate publication time conflicts across member institutions, by implementing a visual calendar scheduling module that enables administrators to assign publication dates and times to approved content, with built-in conflict detection and a consolidated master scheduling view.

3.2. Integrate the Facebook Graph API using Standard Access to automatically publish approved and scheduled content to the connected Facebook Page, achieving a publish success rate of ≥95% within ±5 minutes of the scheduled time across at least 20 test publications during pilot testing. 

3.3. To ensure uninterrupted publishing workflow continuity in the event of API access constraints, by implementing a manual publishing fallback mechanism that enables administrators to directly publish prepared content. 

3.4. Integrate an AI-powered caption generation module using a vision-capable language model (Claude with vision) that produces a suggested caption for ≥90% of uploaded images within 10 seconds of upload completion, with contributors able to accept, edit, or discard each suggestion. Contributor acceptance or accept-with-edits rate shall be ≥60% across at least 20 evaluation submissions. 

3.5. To automate image organization and achieve a classification accuracy of ≥80% on a held-out evaluation set of 50 manually-labeled images, by developing an AI-powered image tagging feature that assigns uploaded images to a predefined set of at least 8 categories (e.g., Awarding Ceremony, Group Photo, Laboratory, Campus Life, Faculty Portrait, Sports Event, Document, Outdoor Shot). 

3.6. Develop an intelligent media recommendation feature that, upon entry of an event title and tags during submission, returns the top 5 related images from the institution's repository within 3 seconds, with ≥70% of contributors rating the top recommendation as "relevant" or "highly relevant" in a usability test with at least 10 participants. 

3.7. To validate that DASIGConnect meets acceptable usability standards (Brooke, 1996\) for its intended users, by administering a System Usability Scale (SUS) survey among designated DASIG contributors and administrators, targeting a minimum SUS score of 70\. 

## 

## **Research Questions**

The following research questions will guide the evaluation of the proposed system:

* To what extent does the implementation of the DASIGConnect system reduce the average posting delay (days from event to publication) compared to the pre-implementation baseline?

* By what percentage does the structured submission and validation workflow increase the content completeness rate (percentage of posts published with all required assets) compared to the baseline?

* What SUS score does the system achieve among DASIG content contributors and administrators, and does this meet the target threshold of 70 or above?

* To what extent does the system increase the posting frequency (posts per month) and consistency of the DASIG Facebook page over the observed deployment period compared to pre-implementation activity?

* To what degree does the AI-assisted caption generation feature reduce caption drafting time, and how do contributors rate the relevance and quality of AI-generated suggestions?

## **PART 3: Methods**

## **Proposed Solution Concept**

The proposed solution is a web-based Social Media Content Workflow and Scheduling Management System designed specifically for the DASIG multi-institutional network. The system addresses the identified problem by replacing the current informal and ad hoc coordination approach with a structured, role-based digital workflow. Content contributors from each member institution—including faculty, student organization officers, and institutional communications officers—will use the system to submit event content including photos, captions, event details, and relevant tags. Designated DASIG administrators, operating under DOST Region 7, will review submitted content through an approval interface, request revisions where necessary, and schedule approved posts for automated publication to the DASIG Facebook page. The system will also maintain a centralized media repository to ensure that assets are preserved and accessible across submission cycles.

To further support contributors during the content preparation stage, the system will incorporate AI-assisted features in a targeted and non-intrusive manner. When a contributor uploads an image during submission, the system will use a vision-capable language model to analyze the image and generate suggested captions relevant to its content, which the contributor may accept, modify, or discard. Additionally, the system will offer an intelligent media recommendation panel that suggests existing photos from the repository based on the event title and tags entered, helping contributors identify supplementary media without manually browsing the full library. These AI features are designed to reduce friction in the submission process rather than replace contributor judgment.

## **Development Methodology**

The project will adopt the Agile Software Development methodology, specifically utilizing iterative sprint cycles guided by the Scrum framework. This approach is appropriate for the project given the need for continuous stakeholder feedback from multiple member institutions and the iterative refinement of features based on real-world usage patterns. Development will proceed in two-week sprints, with each sprint producing a testable increment of the system. Sprint reviews will involve designated DASIG stakeholders to validate progress and realign priorities. This methodology supports structured development, promotes early detection of functional gaps, and allows the team to adapt to changing requirements without disrupting the overall project timeline.

## **Validation Approach**

The system will be evaluated through a combination of functional testing, performance measurement, and user acceptance testing (UAT). Functional testing will verify that each module—submission, validation, scheduling, analytics, and AI assistance—operates correctly under defined use cases. Performance measurement will compare pre-implementation baseline data, gathered through observation of the DASIG Facebook page, against post-implementation metrics. Key performance indicators (KPIs) to be measured include: (1) average posting delay, defined as the number of days between an event occurrence and its publication on the DASIG page; (2) content completeness rate, measured as the percentage of posts published with complete required assets; and (3) posting frequency, measured as the number of posts published per month. AI feature effectiveness will be evaluated through contributor feedback collected via a supplementary survey item measuring perceived usefulness and caption relevance on a 5-point Likert scale. User acceptance will be measured using the System Usability Scale (SUS), targeting a minimum score of 70, which corresponds to an acceptable usability rating. For the purpose of establishing the pre-implementation baseline, content completeness will be assessed using externally observable criteria — specifically, the presence of at least one photo and a caption of at least 50 characters — to enable retrospective analysis of the DASIG Facebook page history. Post-implementation completeness will additionally include server-enforced fields (event date, tags, and mandatory media uploads) as recorded by the system. This distinction will be noted explicitly when comparing pre- and post-implementation completeness rates in the evaluation.

## **PART 4: Expected System**

## **Minimum Viable Product (MVP) Features**

The MVP of the DASIG Content Workflow and Scheduling Management System will include the following core functionalities:

* Multi-organization contributor accounts with role-based access control distinguishing between contributors, validators, and administrators.

* Content submission form with fields for event title, date, description, caption, tags, and media upload (photos and videos).

* AI-assisted caption suggestion panel that analyzes uploaded images and generates contextually relevant caption drafts for contributor review and editing.

* Intelligent media recommendation feature that surfaces related images from the repository based on event title and tags entered during submission.

* Content validation and approval interface allowing administrators to approve, request revision, or reject submitted content with remarks.

* Scheduling module with a calendar view for setting publication dates and times for approved content.

* Centralized media library for organizing uploaded files by institution and event.

* Basic analytics dashboard displaying posting frequency, pending submissions, and average submission-to-publish time.

  ## **High-Level System Workflow**

  The high-level workflow of the system proceeds as follows. A content contributor from a DASIG member institution logs into the system and creates a new content submission by completing the required event details and uploading associated media assets. Upon uploading an image, the system’s AI caption generation module analyzes the image and presents suggested caption text, which the contributor may accept, edit, or replace. The system also displays recommended related images from the repository based on the event context, which the contributor may optionally include. The completed submission enters a pending validation queue accessible to designated DASIG administrators. The administrator reviews the submission, verifies completeness and accuracy, and either approves it, sends it back with revision notes, or rejects it. Upon approval, the administrator assigns a publication schedule using the calendar interface. At the designated time, the system automatically prepares the content for publication. The analytics dashboard continuously reflects updated metrics on submission status, publishing frequency, and content completeness, providing administrators with real-time visibility into the workflow.

## **PART 5: Discussion**

### **Scope**

The system is scoped to support the social media content coordination operations of the DASIG network under DOST Region 7, specifically for its official Facebook page. The system will accommodate content submission from a defined set of member institutions identified by DASIG. Supported content types include event announcements, event highlights, and activity recaps, with media support for image and video uploads. The scheduling module covers Facebook post scheduling within the platform. The analytics dashboard will cover a defined set of KPIs relevant to posting timeliness and consistency. The AI-assisted features are limited to image-based caption suggestion using a pre-integrated language model API and keyword-based media recommendation from the existing repository; the system does not include autonomous content publishing decisions, AI-generated images, or content moderation through automated means. The system does not include integration with social media platforms other than Facebook in its initial version. Facebook API integration will be configured for Standard Access under Meta's developer platform, which supports all required publishing operations for users with administrative roles on the connected Facebook Page. Full public post visibility is contingent on the completion of Meta Business Verification by the DASIG organization and the subsequent transition of the application to Live mode.

### **Limitations**

The project is subject to the following constraints. First, the system requires reliable internet connectivity at contributor and administrator endpoints, which may affect usability in areas with poor network infrastructure. Second, the evaluation period for post-implementation KPI measurement is limited to the available deployment window within the second semester, which may not capture long-term usage patterns. Third, the baseline data for pre-implementation metrics will be gathered through retrospective observation of the DASIG Facebook page posting history, which may not fully reflect all coordination activities. Fourth, the automated publishing functionality of DASIGConnect depends on the Facebook Graph API operating under specific access and visibility conditions that are subject to Meta's platform policies. During the development and evaluation phases of this project, the system's Facebook application will operate under Development mode as defined by Meta's developer platform — a configuration that restricts the visibility of API-published posts to users who hold assigned roles on the registered Meta Developer Application, specifically Page administrators and designated application testers. This means that during the project's deployment and evaluation window, posts published through DASIGConnect's scheduling module will be visible in the DASIG Facebook Page feed only to authorized users with developer or administrator roles, and not to the general public or page followers. This behavior is a documented and expected characteristic of Meta's developer platform, not a functional defect of the system. To transition to full public visibility — wherein all published posts appear on the DASIG Facebook Page's public feed accessible to any Facebook user — the DASIGConnect application must be switched from Development mode to Live mode on the Meta Developer Platform. This transition requires Meta Business Verification, a formal process in which the operating organization — in this case, the DOST Acadême–Science and Innovation Group (DASIG) under DOST Region 7 — must submit official business credentials, documentation, and contact information to Meta for identity verification. This verification process is governed entirely by Meta's policies and is contingent on DASIG's organizational decision to formally adopt and deploy DASIGConnect as an operational tool. As this decision falls outside the jurisdiction and timeline of the academic capstone project, the transition to Live mode and full public post visibility is treated as a post-deployment requirement dependent on DASIG's institutional authorization. The proposed system is designed such that the Development-to-Live transition will require no changes to the codebase — only the completion of the organizational verification process on Meta's platform. Furthermore, once DASIG grants the system access to the official DASIG Facebook Page as an administrator-level integration, the Page Access Token configuration will be updated to point to the production page without requiring any architectural modifications to the platform . Fifth, the accuracy of AI-generated caption suggestions is dependent on the capabilities of the integrated language model API and may vary based on image quality and content clarity; contributors retain full editorial control over all published captions.

## **Expected Contribution**

The proposed system is expected to contribute to the DASIG network’s operations by replacing the current informal, fragmented coordination approach with a structured, traceable, and efficient digital workflow. The primary contribution is the measurable reduction in posting delays and improvement in content completeness, directly addressing the identified operational problem. The integration of AI-assisted caption generation and media recommendation features represents an additional practical contribution, as these tools reduce the manual effort required from contributors while maintaining editorial oversight and quality control. Beyond operational improvement, the system contributes a replicable workflow model applicable to other multi-institutional academic networks facing similar social media coordination challenges. From an academic standpoint, this project addresses a documented gap in existing literature by producing a purpose-built solution for multi-organization content coordination in an academic consortium context, supporting future research on AI-augmented digital workflow systems in similar organizational structures.

# **PART 6: Traceability Matrix**

| RRL Finding / Theme | Identified Gap | Research Question | Proposed Function |
| :---- | :---- | :---- | :---- |
| Social media content scheduling improves posting consistency and audience engagement (Kaplan & Haenlein, 2010\) | Lack of structured scheduling in multi-contributor organizations | To what extent does the absence of a scheduling workflow affect posting timeliness? | Content scheduling and calendar module |
| Centralized digital workflow systems reduce coordination errors in multi-stakeholder environments (Turban et al., 2018\) | No centralized submission and validation process across member institutions | How does a centralized submission workflow affect content completeness and accuracy? | Submission portal with multi-organization access |
| Approval-based content moderation improves quality and reduces errors in collaborative publishing (O'Reilly, 2007\) | No validation or approval layer before content is published | Does an approval mechanism reduce incomplete or erroneous posts? | Content validation and approval workflow |
| Inconsistent social media presence negatively affects organizational visibility and stakeholder trust (Aral et al., 2013\) | DASIG page activity is irregular due to uncoordinated contributions | What is the measurable impact of the system on posting frequency and consistency? | Dashboard with posting frequency analytics and KPI tracking |
| Media asset management systems improve retrieval and reuse of organizational content (Baca, 2016\) | Photos and media files are dispersed across contributors with no centralized repository | How does a centralized media repository affect content availability for posts? | Centralized media library / file repository |
| Large language models and vision-language models can generate contextually relevant text from images, reducing manual content creation effort (Brown et al., 2020; Radford et al., 2021\) | Contributors lack support tools for drafting captions, leading to inconsistent post quality and delayed submissions | To what degree does AI-assisted caption generation reduce drafting time and improve contributor satisfaction with content quality? | AI caption suggestion module and intelligent media recommendation feature |
| Timely feedback mechanisms in digital workflow systems reduce bottlenecks and improve contributor responsiveness in multi-stakeholder environments (Turban et al., 2018\) | No automated status notification system exists to alert contributors when submissions are reviewed, causing uncertainty and delayed follow-up actions | To what extent does automated submission-status notification reduce contributor response time and revision round-trips compared to the pre-implementation baseline? | Email and in-app notification system for submission status changes |
| The System Usability Scale (SUS) provides a validated, technology-independent instrument for measuring perceived usability of interactive systems (Brooke, 1996\) | No standardized usability evaluation method has been applied to assess whether the system interface is acceptable to diverse users across multiple DASIG member institutions | What SUS score does DASIGConnect achieve among DASIG contributors and administrators, and does it meet or exceed the threshold of 70? | SUS-based user acceptance evaluation methodology |
| Vision-language models can assign categorical labels to images with high accuracy, enabling automated organization of visual content at scale (Radford et al., 2021\) | Uploaded media assets have no automated categorization, requiring manual tagging and making retrieval and reuse across submission cycles inefficient | To what degree does the AI-powered image classification feature achieve the target accuracy of ≥80% on a held-out evaluation set of 50 manually-labeled images? | AI-powered image classification and auto-tagging feature |

# 

## **PART 7: References**

Aral, S., Dellarocas, C., & Godes, D. (2013). Introduction to the special issue—Social media and business transformation: A framework for research. Information Systems Research, 24(1), 3–13. https://doi.org/10.1287/isre.1120.0470

Baca, M. (Ed.). (2016). Introduction to metadata (3rd ed.). Getty Publications.

Brown, T. B., Mann, B., Ryder, N., Subbiah, M., Kaplan, J., Dhariwal, P., ... & Amodei, D. (2020). Language models are few-shot learners. Advances in Neural Information Processing Systems, 33, 1877–1901.

Kaplan, A. M., & Haenlein, M. (2010). Users of the world, unite\! The challenges and opportunities of social media. Business Horizons, 53(1), 59–68. https://doi.org/10.1016/j.bushor.2009.09.003

O’Reilly, T. (2007). What is Web 2.0: Design patterns and business models for the next generation of software. Communications & Strategies, 65(1), 17–37.

Radford, A., Kim, J. W., Hallacy, C., Ramesh, A., Goh, G., Agarwal, S., ... & Sutskever, I. (2021). Learning transferable visual models from natural language supervision. Proceedings of the 38th International Conference on Machine Learning, 139, 8748–8763.

Turban, E., Whiteside, J., King, D., & Outland, J. (2018). Introduction to information systems: Supporting and transforming business (6th ed.). Wiley.

Brooke, J. (1996). SUS: A “quick and dirty” usability scale. In P. W. Jordan, B. Thomas, B. A. Weerdmeester, & I. L. McClelland (Eds.), Usability evaluation in industry (pp. 189–194). Taylor & Francis.

Li, J., Li, D., Xiong, C., & Hoi, S. (2022). BLIP: Bootstrapping language-image pre-training for unified vision-language understanding and generation. Proceedings of the 39th International Conference on Machine Learning, 162, 12888–12900.

Schwaber, K., & Sutherland, J. (2020). The Scrum Guide: The definitive guide to Scrum — the rules of the game. Scrum.org. https://scrumguides.org

Van der Aalst, W. M. P. (2016). Process mining: Data science in action (2nd ed.). Springer.

