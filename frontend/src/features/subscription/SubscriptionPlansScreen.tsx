import axios from "axios";
import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import type { SubscriptionPlan } from "../../api/subscriptionApi";
import {
  changeSubscriptionPlan,
  getCurrentSubscription,
  getSubscriptionPlans,
} from "../../api/subscriptionApi";
import type { User } from "../../types/auth.types";
import { useToast } from "../../context/ToastContext";
import { authenticatedQueryMeta } from "../../lib/queryClient";
import { queryKeys, queryRoots } from "../../lib/queryKeys";
import ConfirmDialog from "../user-management/components/ConfirmDialog";
import "../../styles/settings.css";
import "../../styles/subscription-plans.css";

interface Props {
  user: User;
}

const PLAN_STALE_TIME_MS = 60_000;

export default function SubscriptionPlansScreen({ user }: Props) {
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [pendingPlan, setPendingPlan] = useState<SubscriptionPlan | null>(null);
  const scope = { userId: user.id };

  const statusQuery = useQuery({
    queryKey: queryKeys.subscription.status(scope),
    queryFn: ({ signal }) => getCurrentSubscription(signal).then((response) => response.data),
    staleTime: PLAN_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
  const plansQuery = useQuery({
    queryKey: queryKeys.subscription.plans(scope),
    queryFn: ({ signal }) => getSubscriptionPlans(signal).then((response) => response.data),
    staleTime: PLAN_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });
  const changePlanMutation = useMutation({
    mutationFn: changeSubscriptionPlan,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: queryRoots.subscription });
    },
  });

  const plans = useMemo(
    () => [...(plansQuery.data ?? [])].sort((left, right) => left.monthlyPriceCentavos - right.monthlyPriceCentavos),
    [plansQuery.data],
  );
  const isLoading = statusQuery.isLoading || plansQuery.isLoading;
  const error = statusQuery.error ?? plansQuery.error;

  async function retry() {
    await Promise.all([statusQuery.refetch(), plansQuery.refetch()]);
  }

  async function confirmPlanChange() {
    if (!pendingPlan) return;
    try {
      await changePlanMutation.mutateAsync(pendingPlan.id);
      const paidPlan = pendingPlan.monthlyPriceCentavos > 0;
      toast.success(`${displayPlanName(pendingPlan)} is now the selected organization plan.`);
      setPendingPlan(null);
      navigate(paidPlan ? "/settings#subscription" : "/upgrade-plan");
    } catch (changeError) {
      toast.error(errorMessage(changeError, "The organization plan could not be changed."));
    }
  }

  return (
    <div className="subscription-plans-page">
      <header className="subscription-plans-header">
        <button
          type="button"
          className="subscription-plans-back"
          onClick={() => navigate("/settings#subscription")}
          aria-label="Back to Subscription and Billing"
        >
          <i className="ti ti-arrow-left" aria-hidden="true" />
        </button>
        <div>
          <span className="subscription-plans-eyebrow">DASIG Central Visayas</span>
          <h1>Choose your organization plan</h1>
          <p>Plans share capacity across member institutions while preserving institution-level access controls.</p>
        </div>
        <button
          type="button"
          className="subscription-plans-billing-link"
          onClick={() => navigate("/settings#subscription")}
        >
          <i className="ti ti-receipt" aria-hidden="true" />
          Billing & usage
        </button>
      </header>

      {isLoading ? (
        <section className="settings-card subscription-plans-state" aria-busy="true">
          <i className="ti ti-loader-2 settings-spinner" aria-hidden="true" />
          <span>Loading organization plans...</span>
        </section>
      ) : error || !statusQuery.data ? (
        <section className="settings-card subscription-plans-state" role="alert">
          <i className="ti ti-alert-circle" aria-hidden="true" />
          <span>{errorMessage(error, "Organization plans could not be loaded.")}</span>
          <button type="button" className="settings-save-button" onClick={() => void retry()}>
            <i className="ti ti-refresh" aria-hidden="true" /> Retry
          </button>
        </section>
      ) : (
        <>
          <div className="subscription-plans-context" role="status">
            <span>Current plan</span>
            <strong>{displayPlanName(statusQuery.data.plan)}</strong>
            <span>{formatPrice(statusQuery.data.plan.monthlyPriceCentavos)}</span>
          </div>

          <section className="subscription-plans-grid" aria-label="Available subscription plans">
            {plans.map((plan) => {
              const current = plan.id === statusQuery.data?.plan.id;
              const recommended = plan.code === "PLUS_NETWORK";
              return (
                <article
                  className={`subscription-plan-card${current ? " is-current" : ""}${recommended ? " is-recommended" : ""}`}
                  key={plan.id}
                >
                  <div className="subscription-plan-card-topline">
                    {recommended ? <span className="subscription-plan-badge">Recommended</span> : <span />}
                    {current && <span className="subscription-plan-current"><i className="ti ti-check" /> Current</span>}
                  </div>
                  <div className="subscription-plan-card-heading">
                    <h2>{displayPlanName(plan)}</h2>
                    <p>{planDescription(plan)}</p>
                  </div>
                  <div className="subscription-plan-price">
                    <strong>{formatPriceAmount(plan.monthlyPriceCentavos)}</strong>
                    <span>{plan.monthlyPriceCentavos === 0 ? "for pilot use" : "per month"}</span>
                  </div>
                  <button
                    type="button"
                    className={`subscription-plan-action${recommended && !current ? " primary" : ""}`}
                    disabled={current || !user.adminOwner || changePlanMutation.isPending}
                    onClick={() => setPendingPlan(plan)}
                  >
                    {current
                      ? "Current plan"
                      : !user.adminOwner
                        ? "Admin Owner required"
                        : plan.monthlyPriceCentavos === 0
                          ? "Switch to Free"
                          : `Choose ${displayPlanName(plan)}`}
                  </button>
                  <div className="subscription-plan-divider" />
                  <h3>Plan includes</h3>
                  <ul className="subscription-plan-features">
                    {planFeatures(plan).map((feature) => (
                      <li key={feature}><i className="ti ti-check" aria-hidden="true" /><span>{feature}</span></li>
                    ))}
                  </ul>
                </article>
              );
            })}
          </section>

          {!user.adminOwner && (
            <div className="subscription-plans-owner-note">
              <i className="ti ti-info-circle" aria-hidden="true" />
              Only the Admin Owner can change the organization plan or create payment links.
            </div>
          )}
        </>
      )}

      {pendingPlan && (
        <ConfirmDialog
          title={`Choose ${displayPlanName(pendingPlan)}?`}
          message={pendingPlan.monthlyPriceCentavos > 0
            ? "The new pooled limits take effect immediately. Continue to Subscription & Billing afterward to create or open the QRPh payment link. Current-period usage will not reset."
            : "The Free plan limits take effect immediately. Existing data is preserved, but new activity may be restricted when usage exceeds the lower limits."}
          confirmLabel={changePlanMutation.isPending ? "Changing..." : "Confirm plan"}
          onConfirm={() => void confirmPlanChange()}
          onCancel={() => setPendingPlan(null)}
        />
      )}
    </div>
  );
}

function displayPlanName(plan: SubscriptionPlan) {
  if (plan.code === "FREE_PILOT") return "Free";
  if (plan.code === "PLUS_NETWORK") return "Plus";
  if (plan.code === "PREMIUM_NETWORK") return "Premium";
  return plan.name;
}

function planDescription(plan: SubscriptionPlan) {
  if (plan.code === "FREE_PILOT") return "Core publishing tools for a small pilot group.";
  if (plan.code === "PLUS_NETWORK") return "Balanced capacity for the current DASIG network.";
  if (plan.code === "PREMIUM_NETWORK") return "Higher limits, complete exports, and priority support.";
  return "Organization-wide publishing and AI capacity.";
}

function planFeatures(plan: SubscriptionPlan) {
  return [
    `${plan.institutionLimit.toLocaleString()} member institutions`,
    `${plan.activeUserLimit.toLocaleString()} active users`,
    `${formatBytes(plan.mediaStorageBytes)} pooled media storage`,
    `${plan.monthlySubmissionLimit.toLocaleString()} submissions per month`,
    `${plan.monthlyAiCaptionLimit.toLocaleString()} AI captions per month`,
    `${plan.monthlyAiMediaSuggestionLimit.toLocaleString()} AI media suggestions per month`,
    `${plan.perUserDailyCaptionLimit.toLocaleString()} AI captions per user daily`,
    plan.analyticsRetentionDays === null ? "Full analytics history" : `${plan.analyticsRetentionDays.toLocaleString()} days analytics history`,
    exportLabel(plan.exportLevel),
    supportLabel(plan.supportLevel),
  ];
}

function exportLabel(level: SubscriptionPlan["exportLevel"]) {
  if (level === "CSV_AND_PDF") return "CSV and PDF exports";
  if (level === "CSV") return "CSV exports";
  return "Standard in-app reports";
}

function supportLabel(level: SubscriptionPlan["supportLevel"]) {
  if (level === "PRIORITY_EMAIL") return "Priority email support";
  if (level === "STANDARD_EMAIL") return "Standard email support";
  return "Best-effort support";
}

function formatPriceAmount(centavos: number) {
  if (centavos === 0) return "Free";
  return new Intl.NumberFormat("en-PH", {
    style: "currency",
    currency: "PHP",
    maximumFractionDigits: 0,
  }).format(centavos / 100);
}

function formatPrice(centavos: number) {
  return centavos === 0 ? "Free" : `${formatPriceAmount(centavos)}/month`;
}

function formatBytes(value: number) {
  if (value < 1_000_000_000) return `${(value / 1_000_000).toFixed(0)} MB`;
  return `${(value / 1_000_000_000).toFixed(0)} GB`;
}

function errorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error) && typeof error.response?.data?.error === "string") return error.response.data.error;
  return error instanceof Error && error.message ? error.message : fallback;
}
