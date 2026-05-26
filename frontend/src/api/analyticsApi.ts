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

export interface AnalyticsSummaryDto {
  range: AnalyticsRange | string;
  periodStart: string;
  periodEnd: string;
  averagePostingDelay: KpiMetricDto;
  contentCompleteness: KpiMetricDto;
  totalPostsPublished: KpiMetricDto;
  postsByInstitution: InstitutionPostsDto[];
  aiPerformance: AiPerformanceDto;
  operationalHealth: OperationalHealthDto;
}

export type AnalyticsExportMetric =
  | "posting-delay"
  | "content-completeness"
  | "posts-by-institution"
  | "ai-performance"
  | "operational-health";

export function getAnalyticsSummary(range: AnalyticsRange, signal?: AbortSignal) {
  return api.get<AnalyticsSummaryDto>("/analytics/summary", {
    params: { range },
    signal,
  });
}

export async function downloadAnalyticsCsv(metric: AnalyticsExportMetric, range: AnalyticsRange) {
  const response = await api.get<string>(`/analytics/export/${metric}`, {
    params: { range },
    responseType: "text",
  });
  const blob = new Blob([response.data], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `dasigconnect-${metric}-${range}.csv`;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
