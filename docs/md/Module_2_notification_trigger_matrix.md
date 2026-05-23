# Module 2 Notification Trigger Matrix (Stub)

Purpose: Define notification events, recipients, channels, and deep links for UC-2.3/UC-2.1.

## Event Matrix

| Event Code                  | Trigger                                             | Recipient Role(s)  | Channel(s)    | Deep Link            | Notes                             |
| --------------------------- | --------------------------------------------------- | ------------------ | ------------- | -------------------- | --------------------------------- |
| submission_pending          | Submission submitted (DRAFT -> PENDING)             | Validator          | In-app        | /dashboard           | Implemented in SubmissionService. |
| submission_approved         | Validator approves (PENDING/IN_REVIEW -> SCHEDULED) | Contributor        | In-app, email | /dashboard           | Pending ValidationService.        |
| submission_needs_revision   | Validator requests revision                         | Contributor        | In-app, email | /submissions/new     | Include remarks.                  |
| submission_rejected         | Validator rejects                                   | Contributor        | In-app, email | /dashboard           | Include reason code.              |
| submission_publish_failed   | Publish failed                                      | Admin, Contributor | In-app, email | /resolution/failures | Module 3.                         |
| submission_published        | Publish succeeded                                   | Contributor        | In-app, email | /dashboard           | Module 3.                         |
| submission_published_manual | Manual publish complete                             | Contributor        | In-app, email | /dashboard           | Module 3.                         |
| validation_timeout          | Validation timeout escalation                       | Admin, Validator   | In-app, email | /dashboard           | Module 2 job.                     |
| override_denied             | Override denied                                     | Contributor        | In-app, email | /dashboard           | Module 3.                         |
| token_expiring              | Token expiring soon                                 | Admin              | In-app, email | /settings/tokens     | Module 3.                         |
| token_invalid               | Token invalid                                       | Admin              | In-app, email | /settings/tokens     | Module 3.                         |

## Deep Link Conventions

- /dashboard: role-aware landing.
- /submissions/new: contributor submission form.
- /resolution/failures: admin resolution center.
- /settings/tokens: admin token management.

## Notes

- Email templates are handled by BE-1.
- In-app notifications are handled by BE-2.
