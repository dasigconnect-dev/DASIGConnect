import { api } from "./authApi";

export interface ApiResponse<T> {
  data: T;
  message?: string;
}

export interface TaskTelemetryDto {
  id: string;
  userRole: string;
  taskName: string;
  targetEntityId?: string | null;
  startedAt: string;
  completedAt?: string | null;
  durationMs?: number | null;
  attemptNumber: number;
  firstAttemptSuccess: boolean;
  errorCount: number;
  status: "STARTED" | "COMPLETED" | "ABANDONED";
}

export interface RoleTaskSummaryDto {
  userRole: string;
  completedTasks: number;
  firstAttemptSuccessCount: number;
  firstAttemptCompletionRatePercent: number;
  medianDurationSeconds: number;
}

export async function logTaskStart(
  taskName: string,
  targetEntityId?: string | null,
): Promise<ApiResponse<TaskTelemetryDto>> {
  const { data } = await api.post<ApiResponse<TaskTelemetryDto>>("/telemetry/tasks/start", {
    taskName,
    targetEntityId: targetEntityId || undefined,
  });
  return data;
}

export async function logTaskComplete(payload: {
  taskName: string;
  targetEntityId?: string | null;
  durationMs?: number;
  errorCount?: number;
  firstAttemptSuccess?: boolean;
}): Promise<ApiResponse<TaskTelemetryDto>> {
  const { data } = await api.post<ApiResponse<TaskTelemetryDto>>("/telemetry/tasks/complete", {
    taskName: payload.taskName,
    targetEntityId: payload.targetEntityId || undefined,
    durationMs: payload.durationMs,
    errorCount: payload.errorCount ?? 0,
    firstAttemptSuccess: payload.firstAttemptSuccess,
  });
  return data;
}

export async function getTaskTelemetrySummary(): Promise<ApiResponse<RoleTaskSummaryDto[]>> {
  const { data } = await api.get<ApiResponse<RoleTaskSummaryDto[]>>("/telemetry/tasks/summary");
  return data;
}

export interface NextActionDto {
  kind: string;
  label: string;
  count: number;
  deepLink: string;
}

export async function getNextAction(): Promise<ApiResponse<NextActionDto>> {
  const { data } = await api.get<ApiResponse<NextActionDto>>("/me/next-action");
  return data;
}

export async function recordTaskEvent(payload: {
  taskType: string;
  submissionId?: string | null;
  event: "started" | "completed" | "abandoned";
  clientSessionId?: string;
}): Promise<ApiResponse<TaskTelemetryDto>> {
  const { data } = await api.post<ApiResponse<TaskTelemetryDto>>("/task-events", payload);
  return data;
}

