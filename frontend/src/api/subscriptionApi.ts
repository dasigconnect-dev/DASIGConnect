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

export interface SubscriptionBilling {
  providerEnabled: boolean;
  providerMode: "TEST" | "LIVE";
  invoices: SubscriptionInvoice[];
}

export interface SubscriptionInvoice {
  id: string;
  invoiceNumber: string;
  amountCentavos: number;
  currency: string;
  status: "OPEN" | "PAID" | "VOID" | "PARTIALLY_REFUNDED" | "REFUNDED";
  billingPeriodStart: string;
  billingPeriodEnd: string;
  dueAt: string;
  paidAt: string | null;
  createdAt: string;
  paymentLink: SubscriptionPaymentLink | null;
  payments: SubscriptionPayment[];
}

export interface SubscriptionPaymentLink {
  id: string;
  checkoutUrl: string;
  providerReferenceNumber: string | null;
  status: "ACTIVE" | "PAID" | "ARCHIVED";
  liveMode: boolean;
  createdAt: string;
}

export interface SubscriptionPayment {
  id: string;
  externalPaymentId: string;
  amountCentavos: number;
  currency: string;
  status: "PAID" | "PARTIALLY_REFUNDED" | "REFUNDED";
  paidAt: string;
}

export interface CreatedSubscriptionPaymentLink {
  invoiceId: string;
  invoiceNumber: string;
  amountCentavos: number;
  currency: string;
  checkoutUrl: string;
  providerReferenceNumber: string | null;
  status: string;
  liveMode: boolean;
  dueAt: string;
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

export function recordManualSubscriptionRenewal(billingMonths = 1, paymentReference?: string) {
  return api.post<SubscriptionStatus>("/subscription/admin/record-renewal", {
    billingMonths,
    paymentReference: paymentReference || null,
  });
}

export function extendSubscriptionGracePeriod(days = 7) {
  return api.post<SubscriptionStatus>("/subscription/admin/extend-grace-period", { days });
}

export function getSubscriptionBilling(signal?: AbortSignal) {
  return api.get<SubscriptionBilling>("/subscription/billing", { signal });
}

export function createSubscriptionPaymentLink(payerEmail?: string) {
  return api.post<CreatedSubscriptionPaymentLink>("/subscription/billing/payment-link", {
    payerEmail: payerEmail || null,
  });
}

export function reconcileSubscriptionBilling() {
  return api.post<{ checked: number; settled: number; failed: number }>(
    "/subscription/billing/reconcile",
  );
}
