import type { AxiosRequestConfig } from "axios";
import { beginExternalTransfer, finishApiRequest } from "../lib/performanceTelemetry";

export const REQUEST_DEADLINES_MS = {
  session: 12_000,
  logout: 5_000,
  read: 20_000,
  mutation: 30_000,
  ai: 45_000,
  export: 60_000,
  transfer: 5 * 60_000,
} as const;

export function getRequestDeadlineMs(
  config: Pick<AxiosRequestConfig, "url" | "method" | "data" | "responseType">,
): number {
  const url = config.url ?? "";
  const method = (config.method ?? "get").toLowerCase();

  if (url.includes("/auth/logout")) return REQUEST_DEADLINES_MS.logout;
  if (url.includes("/ai/")) return REQUEST_DEADLINES_MS.ai;
  if (
    url === "/me" ||
    url.includes("/auth/") ||
    url.includes("/invitations/validate") ||
    url.includes("/invitations/accept")
  ) {
    return REQUEST_DEADLINES_MS.session;
  }
  if (
    config.responseType === "text" ||
    config.responseType === "blob" ||
    config.responseType === "arraybuffer"
  ) {
    return REQUEST_DEADLINES_MS.export;
  }
  if (typeof FormData !== "undefined" && config.data instanceof FormData) {
    return REQUEST_DEADLINES_MS.transfer;
  }
  return method === "get" || method === "head"
    ? REQUEST_DEADLINES_MS.read
    : REQUEST_DEADLINES_MS.mutation;
}

export function isRequestDeadlineError(error: unknown): boolean {
  if (typeof error !== "object" || error === null) return false;
  const candidate = error as { code?: string };
  return candidate.code === "ECONNABORTED" || candidate.code === "ETIMEDOUT";
}

export async function fetchWithDeadline(
  input: RequestInfo | URL,
  init: RequestInit,
  deadlineMs = REQUEST_DEADLINES_MS.transfer,
): Promise<Response> {
  const requestTimer = beginExternalTransfer(init.method);
  const controller = new AbortController();
  const parentSignal = init.signal;
  let deadlineReached = false;
  const abortFromParent = () => controller.abort();
  if (parentSignal?.aborted) controller.abort();
  parentSignal?.addEventListener("abort", abortFromParent, { once: true });
  const deadlineTimer = window.setTimeout(() => {
    deadlineReached = true;
    controller.abort();
  }, deadlineMs);
  try {
    const response = await fetch(input, { ...init, signal: controller.signal });
    finishApiRequest(requestTimer, response.ok ? "success" : "error", response.status);
    return response;
  } catch (error) {
    if (deadlineReached) {
      finishApiRequest(requestTimer, "timeout");
      const timeoutError = new Error("Request timed out.") as Error & { code: string };
      timeoutError.code = "ETIMEDOUT";
      throw timeoutError;
    }
    finishApiRequest(requestTimer, controller.signal.aborted ? "cancel" : "error");
    throw error;
  } finally {
    window.clearTimeout(deadlineTimer);
    parentSignal?.removeEventListener("abort", abortFromParent);
  }
}
