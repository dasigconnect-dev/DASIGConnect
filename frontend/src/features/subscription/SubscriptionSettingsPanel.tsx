import axios from "axios";
import { useMemo, useState, type ReactNode } from "react";
import type { SubscriptionUsageMetric } from "../../api/subscriptionApi";
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
  const {
    statusQuery,
    plansQuery,
    usageQuery,
    institutionsQuery,
    reconciliationQuery,
    changePlanMutation,
  } = useSubscriptionData(userId);
  const [selectedPlanId, setSelectedPlanId] = useState("");
  const [confirmOpen, setConfirmOpen] = useState(false);

  const queries = [statusQuery, plansQuery, usageQuery, institutionsQuery, reconciliationQuery];
  const isLoading = queries.some((query) => query.isLoading);
  const firstError = queries.find((query) => query.error)?.error;
  const effectiveSelectedPlanId = selectedPlanId || statusQuery.data?.plan.id || "";
  const selectedPlan = plansQuery.data?.find((plan) => plan.id === effectiveSelectedPlanId);
  const warningCount = useMemo(
    () => usageQuery.data?.metrics.filter((metric) => percentage(metric) >= 80).length ?? 0,
    [usageQuery.data?.metrics],
  );

  async function retryAll() {
    await Promise.all(queries.map((query) => query.refetch()));
  }

  async function confirmPlanChange() {
    if (!selectedPlan || selectedPlan.id === statusQuery.data?.plan.id) return;
    try {
      await changePlanMutation.mutateAsync(selectedPlan.id);
      setConfirmOpen(false);
      toast.success(`Subscription changed to ${selectedPlan.name}.`);
    } catch (error) {
      toast.error(errorMessage(error, "The subscription plan could not be changed."));
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

          {isOwner && plansQuery.data && (
            <div className="subscription-plan-control">
              <div>
                <label htmlFor="subscription-plan-select">Organization plan</label>
                <span>Changes apply immediately without resetting current-period usage.</span>
              </div>
              <select
                id="subscription-plan-select"
                className="settings-input"
                value={effectiveSelectedPlanId}
                disabled={changePlanMutation.isPending}
                onChange={(event) => setSelectedPlanId(event.target.value)}
              >
                {plansQuery.data.map((plan) => (
                  <option key={plan.id} value={plan.id}>
                    {plan.name} - {formatPrice(plan.monthlyPriceCentavos)}
                  </option>
                ))}
              </select>
              <button
                type="button"
                className="settings-save-button"
                disabled={!selectedPlan || selectedPlan.id === status.plan.id || changePlanMutation.isPending}
                onClick={() => setConfirmOpen(true)}
              >
                <i className="ti ti-switch-horizontal" aria-hidden="true" />
                Change plan
              </button>
            </div>
          )}
        </div>
      </section>

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

      {confirmOpen && selectedPlan && (
        <ConfirmDialog
          title={`Change to ${selectedPlan.name}?`}
          message="The new pooled limits take effect immediately. Usage already recorded in the current billing period will not reset."
          confirmLabel={changePlanMutation.isPending ? "Changing..." : "Change plan"}
          onConfirm={() => void confirmPlanChange()}
          onCancel={() => setConfirmOpen(false)}
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
