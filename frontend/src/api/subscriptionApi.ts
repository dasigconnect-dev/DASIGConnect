import { api } from "./authApi";

export type SubscriptionLifecycleStatus =
  | "TRIAL"
  | "ACTIVE"
  | "PAST_DUE"
  | "GRACE_PERIOD"
  | "RESTRICTED"
  | "CANCELLED";

export interface SubscriptionPlan {
  id: string;
  code: string;
  name: string;
  version: number;
  monthlyPriceCentavos: number;
  institutionLimit: number;
  activeUserLimit: number;
  mediaStorageBytes: number;
  monthlySubmissionLimit: number;
  monthlyAiCaptionLimit: number;
  monthlyAiMediaSuggestionLimit: number;
  perUserDailyCaptionLimit: number;
  analyticsRetentionDays: number | null;
  exportLevel: "NONE" | "CSV" | "CSV_AND_PDF";
  supportLevel: "BEST_EFFORT" | "STANDARD_EMAIL" | "PRIORITY_EMAIL";
  effectiveFrom: string;
  effectiveUntil: string | null;
}

export interface SubscriptionStatus {
  organizationId: string;
  organizationName: string;
  organizationSlug: string;
  organizationStatus: "ACTIVE" | "INACTIVE";
  subscriptionId: string;
  subscriptionStatus: SubscriptionLifecycleStatus;
  currentPeriodStart: string;
  currentPeriodEnd: string;
  gracePeriodEndsAt: string | null;
  plan: SubscriptionPlan;
  enforcementEnabled: boolean;
}

export interface SubscriptionUsageMetric {
  resource: string;
  used: number;
  limit: number;
  unit: "COUNT" | "BYTES";
}

export interface SubscriptionUsageSummary {
  organizationId: string;
  month: string;
  periodStart: string;
  periodEnd: string;
  subscriptionStatus: SubscriptionLifecycleStatus;
  enforcementEnabled: boolean;
  metrics: SubscriptionUsageMetric[];
}

export interface InstitutionSubscriptionUsage {
  institutionId: string;
  institutionName: string;
  activeUsers: number;
  mediaStorageBytes: number;
  submissions: number;
  aiCaptions: number;
  aiMediaSuggestions: number;
}

export interface SubscriptionReconciliation {
  organizationId: string;
  measuredAt: string;
  institutionCount: number;
  activeUserCount: number;
  liveMediaStorageBytes: number;
  ledgerMediaStorageBytes: number;
  mediaStorageDriftBytes: number;
}

export function getCurrentSubscription(signal?: AbortSignal) {
  return api.get<SubscriptionStatus>("/subscription", { signal });
}

export function getSubscriptionPlans(signal?: AbortSignal) {
  return api.get<SubscriptionPlan[]>("/subscription/plans", { signal });
}

export function getSubscriptionUsage(signal?: AbortSignal) {
  return api.get<SubscriptionUsageSummary>("/subscription/usage", { signal });
}

export function getInstitutionSubscriptionUsage(signal?: AbortSignal) {
  return api.get<InstitutionSubscriptionUsage[]>("/subscription/usage/institutions", { signal });
}

export function getSubscriptionReconciliation(signal?: AbortSignal) {
  return api.get<SubscriptionReconciliation>("/subscription/usage/reconciliation", { signal });
}

export function changeSubscriptionPlan(planId: string) {
  return api.post<SubscriptionStatus>("/subscription/admin/change-plan", { planId });
}
