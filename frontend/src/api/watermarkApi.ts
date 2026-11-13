import { api } from "./authApi";
import {
  DEFAULT_ANCHOR_INSET_PERCENT,
  normalizeWatermarkAnchor,
  round1,
} from "../components/watermark/watermarkLayout";
import type {
  WatermarkConfiguration,
  WatermarkConfigurationRequest,
  WatermarkElement,
} from "../types/watermark.types";

function normalizeElementFromApi(el: WatermarkElement): WatermarkElement {
  const normalized = normalizeWatermarkAnchor(el);
  const isShape = el.type === "shape";
  return {
    ...el,
    anchor: normalized.anchor,
    xPercent: isShape ? 0 : DEFAULT_ANCHOR_INSET_PERCENT,
    yPercent: isShape ? 0 : DEFAULT_ANCHOR_INSET_PERCENT,
  };
}

function prepareElementForSave(el: WatermarkElement): WatermarkElement {
  const normalized = normalizeWatermarkAnchor(el);
  if (el.type === "shape") {
    const isBottom = normalized.anchor === "bottom";
    return {
      ...el,
      anchor: isBottom ? "bottom" : "top",
      xPercent: 0,
      // Keep yPercent in top-origin form for backward compatibility if an older
      // backend instance treats "bottom" as "freeform".
      yPercent: isBottom ? round1(Math.max(0, 100 - el.heightPercent)) : 0,
      widthPercent: 100,
    };
  }
  return {
    ...el,
    anchor: normalized.anchor,
    xPercent: DEFAULT_ANCHOR_INSET_PERCENT,
    yPercent: DEFAULT_ANCHOR_INSET_PERCENT,
  };
}

export async function getWatermarkConfiguration(signal?: AbortSignal) {
  const response = await api.get<WatermarkConfiguration>("/settings/watermark", { signal });
  if (response.data?.elements) {
    response.data = {
      ...response.data,
      elements: response.data.elements.map(normalizeElementFromApi),
    };
  }
  return response;
}

export async function saveWatermarkConfiguration(request: WatermarkConfigurationRequest) {
  const payload: WatermarkConfigurationRequest = {
    ...request,
    elements: (request.elements || []).map(prepareElementForSave),
  };
  const response = await api.put<WatermarkConfiguration>("/settings/watermark", payload);
  if (response.data?.elements) {
    const sentById = new Map(payload.elements.map((el) => [el.id, el]));
    response.data = {
      ...response.data,
      elements: response.data.elements.map((el, index) => {
        const sent = (el.id ? sentById.get(el.id) : undefined) ?? payload.elements[index];
        return normalizeElementFromApi(el.anchor ? el : { ...el, anchor: sent?.anchor });
      }),
    };
  }
  return response;
}
