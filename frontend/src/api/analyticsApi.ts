import { api } from "./authApi";

export type AnalyticsPresetRange = "7d" | "30d" | "90d" | "ytd";
/** A preset, or an explicit inclusive date range: "YYYY-MM-DD..YYYY-MM-DD" (Philippine time). */
export type AnalyticsRange = AnalyticsPresetRange | `${string}..${string}`;

export interface KpiMetricDto {
  id: string;
  label: string;
  value: number;
  unit: string;
  sampleSize: number;
  target: number | null;
  targetMet: boolean;
  deltaPercent: number | null;
  sparkline: number[];
  secondaryLabel: string | null;
  secondaryValue: number | null;
}

export interface InstitutionPostsDto {
  institutionId: string;
  institutionName: string;
  totalPublished: number;
  automatedPublished: number;
  manualPublished: number;
  adminDirectPosts: number;
}

/** AI Feature Adoption — each rate is used ÷ offered. */
export interface AiPerformanceDto {
  /** Captions generated. */
  captionSuggestionEvents: number;
  /** Generated captions applied (as-is or then edited). */
  captionAcceptedEvents: number;
  captionAcceptanceRate: number;
  /** AI media suggestion sets shown in the picker. */
  mediaRecommendationEvents: number;
  /** Times suggested media was added from the AI tab. */
  mediaRecommendationRelevantEvents: number;
  mediaRecommendationRelevanceRate: number;
  /** Submitted posts Album Auto-Match proposed an album for. */
  albumMatchEvents: number;
  /** Of those, posts that kept an album the AI proposed. */
  albumMatchKeptEvents: number;
  albumMatchKeptRate: number;
  templateDraftsGenerated: number;
  templateDraftsSaved: number;
  templateDraftSaveRate: number;
  proofreadChecks: number;
  proofreadSuggestions: number;
  proofreadFixesApplied: number;
  proofreadApplyRate: number;
  insufficientData: boolean;
}

export interface OperationalHealthDto {
  submissionsEnteredWorkflow: number;
  validationDeadlineRisks: number;
  validationTimeoutRiskRate: number;
  overrideAuditEvents: number;
  overrideRate: number;
  publicationAttempts: number;
  successfulPublicationAttempts: number;
  publishingSuccessRate: number;
  onTimePublications: number;
  onTimePublicationRate: number;
  onTimePublicationTarget: number;
  meetsOnTimePublicationTarget: boolean;
  moderatorActions: number;
}

export interface ContributorBreakdownDto {
  contributorId: string;
  contributorName: string;
  postsSubmitted: number;
  postsPublished: number;
  needsRevisionCount: number;
  revisionCycles: number;
  completenessRate: number;
  averagePostingDelayDays: number;
}

export interface StatusBreakdownDto {
  status: string;
  count: number;
}

export interface ContentIssueDto {
  issue: string;
  count: number;
}

export interface InstitutionFilterOptionDto {
  institutionId: string;
  institutionName: string;
}

export interface ContributorAnalyticsDto {
  submittedPosts: number;
  publishedPosts: number;
  revisionRequestCount: number;
  rejectedOrNeedsRevisionCount: number;
  rejectedOrNeedsRevisionRate: number;
}

export interface ValidatorAnalyticsDto {
  institutionSubmissionVolume: number;
  pendingReviewCount: number;
  inReviewCount: number;
  averageValidationTurnaroundDays: number;
  queueAgingOver24Hours: number;
}

export interface AdminAnalyticsDto {
  facebookApiFailureCount: number;
  moderatorActions: number;
  adminDirectPosts: number;
}

export interface FacebookEngagementSummaryDto {
  averageReach: number;
  totalReactions: number;
  totalComments: number;
  totalShares: number;
  sampleSize: number;
  pendingCount: number;
  /** Connected Facebook Page id, for deep-linking admins to Meta's own reach insights. */
  pageId: string | null;
}

export interface PagePerformanceDto {
  reach: number;
  engagements: number;
  newFollows: number;
  views: number;
  pageId: string | null;
  periodStart: string;
  periodEnd: string;
}

export interface AnalyticsSummaryDto {
  range: AnalyticsRange | string;
  periodStart: string;
  periodEnd: string;
  lastUpdated: string;
  scopeRole: string;
  adminView: boolean;
  /** Admin's institution filter; empty = all institutions (always empty for other roles). */
  selectedInstitutionIds: string[];
  institutionFilterOptions: InstitutionFilterOptionDto[];
  averagePostingDelay: KpiMetricDto;
  contentCompleteness: KpiMetricDto;
  totalPostsPublished: KpiMetricDto;
  postsByInstitution: InstitutionPostsDto[];
  contributorBreakdown: ContributorBreakdownDto[];
  statusBreakdown: StatusBreakdownDto[];
  contentIssues: ContentIssueDto[];
  contributorAnalytics: ContributorAnalyticsDto | null;
  validatorAnalytics: ValidatorAnalyticsDto | null;
  aiPerformance: AiPerformanceDto | null;
  adminAnalytics: AdminAnalyticsDto | null;
  operationalHealth: OperationalHealthDto | null;
  facebookEngagement: FacebookEngagementSummaryDto;
  pagePerformance: PagePerformanceDto | null;
}

export interface DailyAnalyticsPointDto {
  date: string;
  value: number;
  secondaryValue: number | null;
}

export interface SubmissionAnalyticsRowDto {
  submissionId: string;
  eventTitle: string;
  firstSubmittedAt: string | null;
  publishedAt: string | null;
  publicationState: string;
  postingDelayDays: number;
  complete: boolean;
  contributorName: string | null;
  institutionName: string | null;
  revisionCycles: number | null;
}

export interface AnalyticsReportDto {
  metric: AnalyticsExportMetric;
  range: AnalyticsRange | string;
  periodStart: string;
  periodEnd: string;
  dailyBreakdown: DailyAnalyticsPointDto[];
  aggregateRows: Array<Record<string, string | number | boolean | null>>;
  totalCount: number;
  page: number;
  pageSize: number;
}

export type AnalyticsExportMetric =
  | "posting-delay"
  | "content-completeness"
  | "posts-by-institution"
  | "ai-performance"
  | "operational-health"
  | "facebook-engagement";

/** Sent as one comma-separated param, which Spring binds to a list. */
function institutionParam(institutionIds: string[]) {
  return institutionIds.length > 0 ? { institutionId: institutionIds.join(",") } : {};
}

export function getAnalyticsSummary(
  range: AnalyticsRange,
  institutionIds: string[] = [],
  signal?: AbortSignal,
) {
  return api.get<AnalyticsSummaryDto>("/analytics/summary", {
    params: { range, ...institutionParam(institutionIds) },
    signal,
  });
}

export function getAnalyticsReport(
  metric: AnalyticsExportMetric,
  range: AnalyticsRange,
  institutionIds: string[] = [],
  page = 1,
  pageSize = 50,
  signal?: AbortSignal,
) {
  return api.get<AnalyticsReportDto>(`/analytics/report/${metric}`, {
    params: { range, page, pageSize, ...institutionParam(institutionIds) },
    signal,
  });
}

export async function downloadAnalyticsCsv(
  metric: AnalyticsExportMetric,
  range: AnalyticsRange,
  institutionIds: string[] = [],
) {
  const response = await api.get<string>(`/analytics/export/${metric}`, {
    params: { range, ...institutionParam(institutionIds) },
    responseType: "text",
  });
  const blob = new Blob([response.data], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  const contentDisposition = response.headers["content-disposition"];
  const headerFilename = typeof contentDisposition === "string"
    ? contentDisposition.match(/filename="?([^"]+)"?/)?.[1]
    : null;
  link.href = url;
  link.download = headerFilename ?? `DASIGConnect_Analytics_${metric}_${range}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
