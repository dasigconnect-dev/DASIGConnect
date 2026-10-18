import axios from "axios";
import { useMemo, useState, type ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import type { SubscriptionStatus, SubscriptionUsageMetric } from "../../api/subscriptionApi";
import { useToast } from "../../context/ToastContext";
import ConfirmDialog from "../user-management/components/ConfirmDialog";
import { useSubscriptionData } from "./hooks/useSubscriptionData";

interface Props {
  userId?: string | null;
  isOwner: boolean;
}

const RESOURCE_LABELS: Record<string, string> = {
  institutions: "Institutions",
  activeUsers: "Active users",
  mediaStorage: "Media storage",
  submissions: "Submissions",
  aiCaptions: "AI captions",
  aiMediaSuggestions: "AI media suggestions",
};

export default function SubscriptionSettingsPanel({ userId, isOwner }: Props) {
  const toast = useToast();
  const navigate = useNavigate();
  const {
    statusQuery,
    usageQuery,
    institutionsQuery,
    reconciliationQuery,
    billingQuery,
    renewalMutation,
    extendGraceMutation,
    createPaymentLinkMutation,
    reconcileBillingMutation,
  } = useSubscriptionData(userId, isOwner);
  const [renewalConfirmOpen, setRenewalConfirmOpen] = useState(false);
  const [graceConfirmOpen, setGraceConfirmOpen] = useState(false);
  const [paymentLinkConfirmOpen, setPaymentLinkConfirmOpen] = useState(false);
  const [payerEmail, setPayerEmail] = useState("");

  const queries = [statusQuery, usageQuery, institutionsQuery, reconciliationQuery];
  const isLoading = queries.some((query) => query.isLoading);
  const firstError = queries.find((query) => query.error)?.error;
  const warningCount = useMemo(
    () => usageQuery.data?.metrics.filter((metric) => percentage(metric) >= 80).length ?? 0,
    [usageQuery.data?.metrics],
  );

  async function retryAll() {
    await Promise.all(queries.map((query) => query.refetch()));
  }

  async function confirmRenewal() {
    try {
      await renewalMutation.mutateAsync();
      setRenewalConfirmOpen(false);
      toast.success("One-month subscription renewal recorded.");
    } catch (error) {
      toast.error(errorMessage(error, "The subscription renewal could not be recorded."));
    }
  }

  async function confirmGraceExtension() {
    try {
      await extendGraceMutation.mutateAsync();
      setGraceConfirmOpen(false);
      toast.success("Grace period extended by seven days.");
    } catch (error) {
      toast.error(errorMessage(error, "The grace period could not be extended."));
    }
  }

  async function confirmPaymentLink() {
    try {
      const response = await createPaymentLinkMutation.mutateAsync(payerEmail.trim() || undefined);
      setPaymentLinkConfirmOpen(false);
      toast.success(`QRPh payment link ready for ${response.data.invoiceNumber}.`);
      window.open(response.data.checkoutUrl, "_blank", "noopener,noreferrer");
    } catch (error) {
      toast.error(errorMessage(error, "The QRPh payment link could not be created."));
    }
  }

  async function reconcileBilling() {
    try {
      const response = await reconcileBillingMutation.mutateAsync();
      toast.success(`Checked ${response.data.checked} payment link(s); settled ${response.data.settled}.`);
    } catch (error) {
      toast.error(errorMessage(error, "Billing reconciliation could not be completed."));
    }
  }

  if (isLoading) {
    return (
      <section className="settings-card subscription-state-card" aria-busy="true">
        <i className="ti ti-loader-2 settings-spinner" aria-hidden="true" />
        <span>Loading subscription usage...</span>
      </section>
    );
  }

  if (firstError || !statusQuery.data || !usageQuery.data) {
    return (
      <section className="settings-card subscription-state-card" role="alert">
        <i className="ti ti-alert-circle" aria-hidden="true" />
        <span>{errorMessage(firstError, "Subscription information could not be loaded.")}</span>
        <button type="button" className="settings-save-button" onClick={() => void retryAll()}>
          <i className="ti ti-refresh" aria-hidden="true" />
          Retry
        </button>
      </section>
    );
  }

  const status = statusQuery.data;
  const reconciliation = reconciliationQuery.data;
  const hasStorageDrift = reconciliation?.mediaStorageDriftBytes !== 0;

  return (
    <div className="subscription-settings-panel">
      <section className="settings-card">
        <SettingsHeader
          icon="ti ti-receipt"
          title="Subscription & Billing"
          description={`${status.organizationName} organization plan and billing period.`}
          accent={
            <span className={`subscription-status subscription-status-${status.subscriptionStatus.toLowerCase()}`}>
              {formatStatus(status.subscriptionStatus)}
            </span>
          }
        />
        <div className="settings-card-body">
          {lifecycleNotice(status)}
          <div className="subscription-plan-summary">
            <div className="subscription-current-plan">
              <span className="subscription-eyebrow">Current plan</span>
              <strong>{status.plan.name}</strong>
              <span>{formatPrice(status.plan.monthlyPriceCentavos)}</span>
            </div>
            <dl>
              <div><dt>Billing period</dt><dd>{formatDate(status.currentPeriodStart)} - {formatDate(status.currentPeriodEnd)}</dd></div>
              <div><dt>Usage resets</dt><dd>{formatDate(status.currentPeriodEnd)}</dd></div>
              <div><dt>Enforcement</dt><dd>{status.enforcementEnabled ? "Enabled" : "Shadow mode"}</dd></div>
              <div><dt>Usage warnings</dt><dd>{warningCount}</dd></div>
            </dl>
          </div>

          <div className="subscription-plan-control">
            <div>
              <label>Organization plan</label>
              <span>Compare plan limits, AI allowances, storage, exports, and support.</span>
            </div>
            <div>
              <label>Current tier</label>
              <span>{status.plan.name} at {formatPrice(status.plan.monthlyPriceCentavos)}</span>
            </div>
            <button
              type="button"
              className="settings-save-button"
              onClick={() => navigate("/upgrade-plan")}
            >
              <i className="ti ti-layout-grid" aria-hidden="true" />
              Compare plans
            </button>
          </div>

          {isOwner && (
            <div className="subscription-lifecycle-controls">
              <div>
                <strong>Manual billing controls</strong>
                <span>Use only after confirming payment or an approved grace extension outside DASIGConnect.</span>
              </div>
              {isOverdue(status.subscriptionStatus) && (
                <button
                  type="button"
                  className="settings-save-button subscription-secondary-action"
                  disabled={extendGraceMutation.isPending || renewalMutation.isPending}
                  onClick={() => setGraceConfirmOpen(true)}
                >
                  <i className="ti ti-calendar-plus" aria-hidden="true" />
                  Extend 7 days
                </button>
              )}
              <button
                type="button"
                className="settings-save-button"
                disabled={renewalMutation.isPending || extendGraceMutation.isPending}
                onClick={() => setRenewalConfirmOpen(true)}
              >
                <i className="ti ti-calendar-check" aria-hidden="true" />
                Record 1-month renewal
              </button>
            </div>
          )}
        </div>
      </section>

      {isOwner && (
        <section className="settings-card">
          <SettingsHeader
            icon="ti ti-qrcode"
            title="QRPh Billing"
            description="Hosted PayMongo payment links and verified organization payment history."
            accent={billingQuery.data ? (
              <span className={`subscription-provider-mode ${billingQuery.data.providerMode.toLowerCase()}`}>
                {billingQuery.data.providerEnabled ? billingQuery.data.providerMode : "Disabled"}
              </span>
            ) : undefined}
          />
          <div className="settings-card-body">
            {billingQuery.isLoading ? (
              <div className="subscription-inline-state" aria-busy="true">
                <i className="ti ti-loader-2 settings-spinner" aria-hidden="true" /> Loading billing history...
              </div>
            ) : billingQuery.error ? (
              <div className="subscription-inline-state" role="alert">
                <i className="ti ti-alert-circle" aria-hidden="true" />
                <span>{errorMessage(billingQuery.error, "Billing history could not be loaded.")}</span>
                <button type="button" className="settings-save-button" onClick={() => void billingQuery.refetch()}>
                  <i className="ti ti-refresh" aria-hidden="true" /> Retry
                </button>
              </div>
            ) : billingQuery.data && (
              <>
                <div className="subscription-payment-controls">
                  <div>
                    <label htmlFor="subscription-payer-email">Payer email</label>
                    <span>Optional. Sends the hosted payment link to the authorized payer.</span>
                  </div>
                  <input
                    id="subscription-payer-email"
                    className="settings-input"
                    type="email"
                    value={payerEmail}
                    placeholder="billing@example.org"
                    onChange={(event) => setPayerEmail(event.target.value)}
                  />
                  <button
                    type="button"
                    className="settings-save-button subscription-secondary-action"
                    disabled={!billingQuery.data.providerEnabled || reconcileBillingMutation.isPending}
                    onClick={() => void reconcileBilling()}
                  >
                    <i className="ti ti-refresh" aria-hidden="true" /> Reconcile
                  </button>
                  <button
                    type="button"
                    className="settings-save-button"
                    disabled={!billingQuery.data.providerEnabled || status.plan.monthlyPriceCentavos <= 0 || createPaymentLinkMutation.isPending}
                    onClick={() => setPaymentLinkConfirmOpen(true)}
                  >
                    <i className="ti ti-qrcode" aria-hidden="true" /> Get payment link
                  </button>
                </div>

                <div className="subscription-table-wrap">
                  <table className="subscription-table subscription-billing-table">
                    <thead><tr><th>Invoice</th><th>Amount</th><th>Status</th><th>Due</th><th>Payment</th></tr></thead>
                    <tbody>
                      {billingQuery.data.invoices.map((invoice) => (
                        <tr key={invoice.id}>
                          <td>{invoice.invoiceNumber}</td>
                          <td>{formatCurrency(invoice.amountCentavos, invoice.currency)}</td>
                          <td><span className={`subscription-invoice-status is-${invoice.status.toLowerCase()}`}>{formatStatus(invoice.status)}</span></td>
                          <td>{formatDate(invoice.dueAt)}</td>
                          <td>
                            {invoice.paymentLink?.status === "ACTIVE" ? (
                              <a href={invoice.paymentLink.checkoutUrl} target="_blank" rel="noreferrer">Open QRPh link</a>
                            ) : invoice.paidAt ? formatDateTime(invoice.paidAt) : "Not available"}
                          </td>
                        </tr>
                      ))}
                      {!billingQuery.data.invoices.length && (
                        <tr><td colSpan={5} className="subscription-empty-row">No subscription invoices yet.</td></tr>
                      )}
                    </tbody>
                  </table>
                </div>
              </>
            )}
          </div>
        </section>
      )}

      <section className="settings-card">
        <SettingsHeader
          icon="ti ti-chart-bar"
          title="Pooled Usage"
          description={`Current organization usage for ${formatMonth(usageQuery.data.month)}.`}
          accent={hasStorageDrift ? (
            <span className="subscription-warning"><i className="ti ti-alert-triangle" /> Reconciliation warning</span>
          ) : undefined}
        />
        <div className="settings-card-body">
          <div className="subscription-usage-grid">
            {usageQuery.data.metrics.map((metric) => <UsageMetric key={metric.resource} metric={metric} />)}
          </div>
          {reconciliation && (
            <div className={`subscription-reconciliation ${hasStorageDrift ? "is-warning" : ""}`}>
              <span><i className={hasStorageDrift ? "ti ti-alert-triangle" : "ti ti-circle-check"} /> Storage reconciliation</span>
              <strong>{hasStorageDrift ? `${formatBytes(Math.abs(reconciliation.mediaStorageDriftBytes))} difference` : "No difference detected"}</strong>
              <time dateTime={reconciliation.measuredAt}>{formatDateTime(reconciliation.measuredAt)}</time>
            </div>
          )}
        </div>
      </section>

      <section className="settings-card">
        <SettingsHeader
          icon="ti ti-building-community"
          title="Institution Usage"
          description="Organization usage grouped by member institution."
        />
        <div className="settings-card-body subscription-table-wrap">
          <table className="subscription-table">
            <thead>
              <tr>
                <th>Institution</th>
                <th>Users</th>
                <th>Storage</th>
                <th>Submissions</th>
                <th>AI captions</th>
                <th>Media suggestions</th>
              </tr>
            </thead>
            <tbody>
              {(institutionsQuery.data ?? []).map((institution) => (
                <tr key={institution.institutionId}>
                  <td>{institution.institutionName}</td>
                  <td>{institution.activeUsers.toLocaleString()}</td>
                  <td>{formatBytes(institution.mediaStorageBytes)}</td>
                  <td>{institution.submissions.toLocaleString()}</td>
                  <td>{institution.aiCaptions.toLocaleString()}</td>
                  <td>{institution.aiMediaSuggestions.toLocaleString()}</td>
                </tr>
              ))}
              {!institutionsQuery.data?.length && (
                <tr><td colSpan={6} className="subscription-empty-row">No institution usage recorded.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {renewalConfirmOpen && (
        <ConfirmDialog
          title="Record one-month renewal?"
          message="Confirm only after payment has been verified outside DASIGConnect. This activates the subscription and extends the billing period by one month."
          confirmLabel={renewalMutation.isPending ? "Recording..." : "Record renewal"}
          onConfirm={() => void confirmRenewal()}
          onCancel={() => setRenewalConfirmOpen(false)}
        />
      )}
      {graceConfirmOpen && (
        <ConfirmDialog
          title="Extend grace period by seven days?"
          message="New billable actions will remain available during the extended grace period. This action is recorded in the audit log."
          confirmLabel={extendGraceMutation.isPending ? "Extending..." : "Extend grace"}
          onConfirm={() => void confirmGraceExtension()}
          onCancel={() => setGraceConfirmOpen(false)}
        />
      )}
      {paymentLinkConfirmOpen && (
        <ConfirmDialog
          title="Create QRPh payment link?"
          message="DASIGConnect will create or reuse the current monthly invoice and open PayMongo's hosted payment page. Access is renewed only after a signed payment confirmation is verified."
          confirmLabel={createPaymentLinkMutation.isPending ? "Creating..." : "Create link"}
          onConfirm={() => void confirmPaymentLink()}
          onCancel={() => setPaymentLinkConfirmOpen(false)}
        />
      )}
    </div>
  );
}

function SettingsHeader({ icon, title, description, accent }: { icon: string; title: string; description: string; accent?: ReactNode }) {
  return (
    <header className="settings-card-header">
      <span className="settings-card-icon"><i className={icon} /></span>
      <div className="settings-card-heading"><h2>{title}</h2><p>{description}</p></div>
      {accent}
    </header>
  );
}

function UsageMetric({ metric }: { metric: SubscriptionUsageMetric }) {
  const percent = percentage(metric);
  return (
    <div className={`subscription-usage-item ${percent >= 100 ? "is-limit" : percent >= 80 ? "is-warning" : ""}`}>
      <div><span>{RESOURCE_LABELS[metric.resource] ?? metric.resource}</span><strong>{formatMetric(metric.used, metric.unit)} / {formatMetric(metric.limit, metric.unit)}</strong></div>
      <div className="subscription-progress" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.min(percent, 100)}>
        <span style={{ width: `${Math.min(percent, 100)}%` }} />
      </div>
      <small>{percent.toFixed(0)}% used</small>
    </div>
  );
}

function percentage(metric: SubscriptionUsageMetric) {
  if (metric.limit <= 0) return metric.used > 0 ? 100 : 0;
  return (metric.used / metric.limit) * 100;
}

function lifecycleNotice(status: SubscriptionStatus) {
  const state = status.subscriptionStatus;
  if (state === "ACTIVE" || state === "TRIAL") return null;
  const copy = state === "PAST_DUE"
    ? "Payment is overdue. Record the verified renewal before the grace period ends."
    : state === "GRACE_PERIOD"
      ? `The subscription is in its grace period${status.gracePeriodEndsAt ? ` until ${formatDateTime(status.gracePeriodEndsAt)}` : ""}.`
      : state === "RESTRICTED"
        ? "New uploads, submissions, invitations, and AI requests are restricted. Existing review and scheduled publishing remain available."
        : "This subscription is cancelled. Contact the Admin Owner before creating new billable activity.";
  return (
    <div className={`subscription-lifecycle-notice is-${state.toLowerCase()}`} role="status">
      <i className={state === "RESTRICTED" || state === "CANCELLED" ? "ti ti-lock" : "ti ti-alert-triangle"} aria-hidden="true" />
      <div><strong>{formatStatus(state)}</strong><span>{copy}</span></div>
    </div>
  );
}

function isOverdue(status: string) {
  return status === "PAST_DUE" || status === "GRACE_PERIOD" || status === "RESTRICTED";
}

function formatMetric(value: number, unit: SubscriptionUsageMetric["unit"]) {
  return unit === "BYTES" ? formatBytes(value) : value.toLocaleString();
}

function formatBytes(value: number) {
  if (value < 1_000_000) return `${(value / 1_000).toFixed(value < 10_000 ? 1 : 0)} KB`;
  if (value < 1_000_000_000) return `${(value / 1_000_000).toFixed(1)} MB`;
  return `${(value / 1_000_000_000).toFixed(1)} GB`;
}

function formatPrice(centavos: number) {
  if (centavos === 0) return "Free";
  return new Intl.NumberFormat("en-PH", { style: "currency", currency: "PHP", maximumFractionDigits: 0 }).format(centavos / 100) + "/month";
}

function formatCurrency(centavos: number, currency: string) {
  return new Intl.NumberFormat("en-PH", {
    style: "currency",
    currency,
    maximumFractionDigits: 2,
  }).format(centavos / 100);
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("en-PH", { dateStyle: "medium", timeZone: "Asia/Manila" }).format(new Date(value));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("en-PH", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Manila" }).format(new Date(value));
}

function formatMonth(value: string) {
  const [year, month] = value.split("-").map(Number);
  return new Intl.DateTimeFormat("en-PH", { month: "long", year: "numeric" }).format(new Date(year, month - 1, 1));
}

function formatStatus(value: string) {
  return value.toLowerCase().replaceAll("_", " ").replace(/\b\w/g, (letter) => letter.toUpperCase());
}

function errorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error) && typeof error.response?.data?.error === "string") return error.response.data.error;
  return error instanceof Error && error.message ? error.message : fallback;
}
