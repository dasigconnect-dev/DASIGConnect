import { api } from "./authApi";
import type { WatermarkConfiguration, WatermarkConfigurationRequest } from "../types/watermark.types";

export async function getWatermarkConfiguration(signal?: AbortSignal) {
  return api.get<WatermarkConfiguration>("/settings/watermark", { signal });
}

export async function saveWatermarkConfiguration(request: WatermarkConfigurationRequest) {
  return api.put<WatermarkConfiguration>("/settings/watermark", request);
}
