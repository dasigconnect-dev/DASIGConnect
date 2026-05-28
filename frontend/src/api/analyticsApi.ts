import { api } from "./authApi";

export type AnalyticsRange = "7d" | "30d" | "90d" | "ytd";

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

export interface AiPerformanceDto {
  captionSuggestionEvents: number;
  captionAcceptedEvents: number;
  captionAcceptanceRate: number;
  tagClassificationEvents: number;
  tagCorrectionEvents: number;
  tagCorrectionRate: number;
  mediaRecommendationEvents: number;
  mediaRecommendationRelevantEvents: number;
  mediaRecommendationRelevanceRate: number;
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
  administratorActions: number;
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

export interface CategoryPerformanceDto {
  category: string;
  postCount: number;
  completenessRate: number;
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
  administratorActions: number;
  adminDirectPosts: number;
}

export interface AnalyticsSummaryDto {
  range: AnalyticsRange | string;
  periodStart: string;
  periodEnd: string;
  lastUpdated: string;
  scopeRole: string;
  adminView: boolean;
  selectedInstitutionId: string | null;
  institutionFilterOptions: InstitutionFilterOptionDto[];
  averagePostingDelay: KpiMetricDto;
  contentCompleteness: KpiMetricDto;
  totalPostsPublished: KpiMetricDto;
  postsByInstitution: InstitutionPostsDto[];
  contributorBreakdown: ContributorBreakdownDto[];
  statusBreakdown: StatusBreakdownDto[];
  contentIssues: ContentIssueDto[];
  topCategories: CategoryPerformanceDto[];
  contributorAnalytics: ContributorAnalyticsDto | null;
  validatorAnalytics: ValidatorAnalyticsDto | null;
  aiPerformance: AiPerformanceDto;
  adminAnalytics: AdminAnalyticsDto | null;
  operationalHealth: OperationalHealthDto | null;
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
  submissions: SubmissionAnalyticsRowDto[];
  aggregateRows: Array<Record<string, string | number | boolean | null>>;
}

export type AnalyticsExportMetric =
  | "posting-delay"
  | "content-completeness"
  | "posts-by-institution"
  | "ai-performance"
  | "operational-health";

export function getAnalyticsSummary(range: AnalyticsRange, institutionId?: string | null, signal?: AbortSignal) {
  return api.get<AnalyticsSummaryDto>("/analytics/summary", {
    params: { range, ...(institutionId ? { institutionId } : {}) },
    signal,
  });
}

export function getAnalyticsReport(
  metric: AnalyticsExportMetric,
  range: AnalyticsRange,
  institutionId?: string | null,
  signal?: AbortSignal,
) {
  return api.get<AnalyticsReportDto>(`/analytics/report/${metric}`, {
    params: { range, ...(institutionId ? { institutionId } : {}) },
    signal,
  });
}

export async function downloadAnalyticsCsv(
  metric: AnalyticsExportMetric,
  range: AnalyticsRange,
  institutionId?: string | null,
) {
  const response = await api.get<string>(`/analytics/export/${metric}`, {
    params: { range, ...(institutionId ? { institutionId } : {}) },
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
