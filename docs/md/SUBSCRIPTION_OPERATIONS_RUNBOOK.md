# DASIGConnect Subscription Operations Runbook

## Purpose

This runbook covers the manual subscription lifecycle and the optional PayMongo QRPh-assisted billing flow. It applies to the single DASIG organization and its pooled member-institution usage.

## Rollout Controls

The lifecycle scheduler is disabled by default. Enable it only after an Admin Owner has confirmed the billing dates shown under `Settings > Subscription & Billing`.

| Environment variable | Default | Purpose |
|---|---:|---|
| `SUBSCRIPTION_LIFECYCLE_ENABLED` | `false` | Enables automatic lifecycle transitions. Set to `false` for immediate rollback. |
| `SUBSCRIPTION_LIFECYCLE_CRON` | `0 10 * * * *` | Runs the lifecycle check hourly at minute 10 UTC. |
| `SUBSCRIPTION_PAST_DUE_NOTICE_DAYS` | `1` | Time between `PAST_DUE` and `GRACE_PERIOD`. |
| `SUBSCRIPTION_GRACE_PERIOD_DAYS` | `7` | Grace duration calculated from the expired billing-period end. |
| `SUBSCRIPTION_ENFORCEMENT_ENABLED` | `false` | Enforces plan limits and restricted-state blocking for new billable actions. |
| `PAYMONGO_ENABLED` | `false` | Enables QRPh payment-link creation and provider reconciliation. |
| `PAYMONGO_MODE` | `TEST` | Selects `TEST` or `LIVE` credentials and webhook verification. |
| `PAYMONGO_API_BASE_URL` | `https://api.paymongo.com/v1` | PayMongo API base URL. Do not override this in live mode. |
| `PAYMONGO_SK_TEST` | empty | Server-only PayMongo test secret key. |
| `PAYMONGO_SK_LIVE` | empty | Server-only PayMongo live secret key. |
| `PAYMONGO_WEBHOOK_SECRET_TEST` | empty | Test webhook signing secret. |
| `PAYMONGO_WEBHOOK_SECRET_LIVE` | empty | Live webhook signing secret. |
| `PAYMONGO_SIGNATURE_TOLERANCE_SECONDS` | `300` | Maximum accepted webhook timestamp age. |
| `PAYMONGO_RECONCILIATION_ENABLED` | `false` | Enables server-to-server checks for active payment links. |
| `PAYMONGO_RECONCILIATION_CRON` | `0 35 * * * *` | Runs reconciliation hourly at minute 35 UTC. |

Recommended rollout:

1. Keep lifecycle and enforcement disabled while verifying production plan limits and period dates.
2. Enable lifecycle first and verify the `Subscription Lifecycle` job in System Health.
3. Review in-app/email delivery and audit entries for each transition.
4. Enable enforcement only after successful shadow-mode usage review.

### PayMongo test rollout

1. Keep `PAYMONGO_MODE=TEST` and use only test credentials.
2. In PayMongo, create one webhook endpoint at `https://<backend-host>/api/v1/webhooks/paymongo`.
3. Subscribe it to `link.payment.paid`, `payment.refund.updated`, and `payment.refunded`.
4. Store the returned test webhook secret in `PAYMONGO_WEBHOOK_SECRET_TEST` and set the test secret API key in `PAYMONGO_SK_TEST`.
5. Set `PAYMONGO_ENABLED=true`, restart the backend, and create a payment link from `Settings > Subscription & Billing` as the Admin Owner.
6. Complete a test QRPh payment and confirm the invoice, subscription period, notification, and audit log update.
7. Enable `PAYMONGO_RECONCILIATION_ENABLED=true` only after the direct webhook flow is verified.

Test and live webhook endpoints have different signing secrets. Do not reuse a test secret in live mode. Browser redirects and frontend state never activate a subscription; activation requires a verified webhook or server-to-server reconciliation.

## Lifecycle

```text
TRIAL or ACTIVE
    -> PAST_DUE when current_period_end is reached
    -> GRACE_PERIOD after the configured notice period
    -> RESTRICTED when grace_period_ends_at is reached
```

`CANCELLED` is reserved for an explicit administrative process and is not set automatically.

Restricted status blocks only new billable activity: uploads, new submissions, invitations/seat commitments, AI captions, and AI media suggestions. Existing review, recovery, scheduling, and publishing workflows remain available.

## Admin Owner Procedures

### Record a verified renewal

1. Verify payment outside DASIGConnect using the approved official record.
2. Open `Settings > Subscription & Billing`.
3. Select **Record 1-month renewal** and confirm.
4. Confirm that status is `ACTIVE`, the billing end date advanced, and the audit log contains `SUBSCRIPTION_MANUAL_RENEWAL_RECORDED`.

Do not use the renewal control based only on a message, screenshot, or browser redirect. When PayMongo is enabled, prefer the verified QRPh flow. Manual renewal remains the fallback for an independently verified payment or an approved offline arrangement.

### Create and verify a QRPh payment link

1. Sign in as the Admin Owner and open `Settings > Subscription & Billing`.
2. Confirm the plan, amount, billing period, and whether the environment badge says `TEST` or `LIVE`.
3. Select **Create QRPh payment link**. Open only the returned PayMongo checkout URL.
4. After payment, return to the billing section and select **Reconcile** if the webhook has not updated the invoice.
5. Confirm that the invoice is `PAID`, the subscription is `ACTIVE`, and the billing period has advanced exactly once.
6. Confirm the audit log contains `SUBSCRIPTION_PAYMONGO_PAYMENT_CONFIRMED`.

Repeated webhook deliveries and repeated reconciliation runs are idempotent. A duplicate provider payment ID must not advance the subscription a second time.

### Refund a payment

1. Verify the payment and refundable balance in the invoice history.
2. Submit the refund as the Admin Owner with an approved reason.
3. Confirm the local refund status. A provider response may initially remain `PENDING`.
4. Verify that `payment.refund.updated` or `payment.refunded` changes the payment and invoice to `PARTIALLY_REFUNDED` or `REFUNDED`.
5. Investigate `subscription_payment_attention` notifications and rejected webhook receipts before retrying.

Refunding a payment records the financial state but does not automatically shorten or cancel an already granted subscription period. Any entitlement correction requires an authorized manual decision and audit record.

### Extend grace

1. Obtain the authorized extension decision.
2. Open `Settings > Subscription & Billing`.
3. Select **Extend 7 days** and confirm.
4. Confirm that status is `GRACE_PERIOD` and the audit log contains `SUBSCRIPTION_GRACE_PERIOD_EXTENDED`.

### Run lifecycle manually

Use `System Health > Background Jobs > Subscription Lifecycle > Run` after correcting a date or restoring scheduler operation. The job is idempotent and advances at most one state per run.

## Monitoring And Alerts

- System Health must show a recent successful `Subscription Lifecycle` run after lifecycle automation is enabled.
- Investigate failed or stale job status before changing subscription dates manually.
- Lifecycle transitions create immutable audit records and administrator in-app/email alerts.
- Monitor pooled usage and storage reconciliation warnings from Subscription & Billing.
- Monitor `Subscription Billing Reconciliation` after PayMongo reconciliation is enabled.
- Review failed or rejected PayMongo webhook receipts and `subscription_payment_attention` notifications.
- Existing scheduled publications must be checked separately; lifecycle restriction intentionally does not stop them.

## Rollback

1. Set `SUBSCRIPTION_LIFECYCLE_ENABLED=false` to stop automatic state transitions.
2. Set `SUBSCRIPTION_ENFORCEMENT_ENABLED=false` if new activity is incorrectly blocked.
3. Set `PAYMONGO_ENABLED=false` and `PAYMONGO_RECONCILIATION_ENABLED=false` to stop new payment links and provider polling.
4. Restart the backend so configuration changes take effect.
5. Use an Admin Owner renewal or grace extension to restore valid access. Do not edit subscription rows directly unless application recovery is impossible.
6. Record the incident, affected dates, actions, and final status in the project operations log.

Disabling lifecycle automation does not reverse a status already written. Disabling enforcement allows activity while the status is investigated.

## Export And Offboarding

Before offboarding or changing providers:

1. Export System Health and Audit Log CSV records.
2. Record the current plan, lifecycle status, billing dates, pooled usage, institution breakdown, and storage reconciliation values.
3. Export any analytics allowed by the current plan and preserve required validation/testing evidence.
4. Confirm all scheduled publications and unresolved failures with the customer.
5. Revoke external provider credentials only after required exports and publishing handoff are complete.
6. Preserve audit and usage ledgers according to the project retention policy; do not delete them as part of normal account closure.

## Two-Month Pilot Checklist

Record at least weekly:

- active institutions and users;
- media storage and reconciliation drift;
- submissions, AI captions, and AI media suggestions consumed;
- warning/limit events and blocked actions;
- lifecycle-job health and notification delivery failures;
- renewal/grace interventions;
- Gemini, Voyage, R2, database, email, and hosting costs;
- customer feedback on limits and operational handling.

Use measured pilot data to revise plan allowances, warning thresholds, grace duration, and the production budget before enabling payment collection.
