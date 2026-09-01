import { api } from "./authApi";
import type { WatermarkConfiguration, WatermarkConfigurationRequest } from "../types/watermark.types";

export async function getWatermarkConfiguration(institutionId?: string | null) {
  const params = institutionId ? { institutionId } : undefined;
  return api.get<WatermarkConfiguration>("/settings/watermark", { params });
}

export async function saveWatermarkConfiguration(request: WatermarkConfigurationRequest) {
  return api.put<WatermarkConfiguration>("/settings/watermark", request);
}
