export interface RevisionFieldsPayload {
  general: string;
  fields: Record<string, string>;
}

export interface ParsedRevisionRemarks {
  general: string;
  fields: Record<string, string>;
  hasFieldComments: boolean;
}

export interface RevisionFieldMeta {
  key: string;
  label: string;
  icon: string;
}

export const REVISION_SUPPORTED_FIELDS: RevisionFieldMeta[] = [
  { key: "caption", label: "Caption", icon: "ti-file-text" },
  { key: "eventTitle", label: "Event Title", icon: "ti-heading" },
  { key: "media", label: "Media Assets", icon: "ti-photo" },
  { key: "eventDate", label: "Event Date", icon: "ti-calendar" },
  { key: "tags", label: "Tags", icon: "ti-tag" },
];

/**
 * Encodes general remarks and field-specific comments into a payload string.
 * If no field comments exist, returns the clean general text.
 */
export function encodeRevisionRemarks(
  general: string,
  fields: Record<string, string>,
): string {
  const cleanedFields: Record<string, string> = {};
  for (const [key, value] of Object.entries(fields)) {
    const trimmed = (value || "").trim();
    if (trimmed.length > 0) {
      cleanedFields[key] = trimmed;
    }
  }

  const cleanedGeneral = (general || "").trim();

  if (Object.keys(cleanedFields).length === 0) {
    return cleanedGeneral;
  }

  return JSON.stringify({
    __dcRevision: true,
    general: cleanedGeneral,
    fields: cleanedFields,
  });
}

/**
 * Parses raw validator remarks from the backend.
 * Handles structured JSON payloads as well as legacy plain-text remarks.
 */
export function parseRevisionRemarks(raw: string | null | undefined): ParsedRevisionRemarks {
  if (!raw || typeof raw !== "string") {
    return { general: "", fields: {}, hasFieldComments: false };
  }

  const trimmed = raw.trim();
  if (trimmed.startsWith("{") && trimmed.includes("__dcRevision")) {
    try {
      const parsed = JSON.parse(trimmed) as {
        __dcRevision?: boolean;
        general?: string;
        fields?: Record<string, string>;
      };
      const fields = parsed.fields && typeof parsed.fields === "object" ? parsed.fields : {};
      const validFields: Record<string, string> = {};
      for (const [k, v] of Object.entries(fields)) {
        if (typeof v === "string" && v.trim().length > 0) {
          validFields[k] = v.trim();
        }
      }
      return {
        general: parsed.general || "",
        fields: validFields,
        hasFieldComments: Object.keys(validFields).length > 0,
      };
    } catch {
      // JSON parse fallback
    }
  }

  // Fallback for plain text remarks
  return {
    general: trimmed,
    fields: {},
    hasFieldComments: false,
  };
}

/**
 * Formats revision remarks into clean, human-readable plain text without any code or JSON syntax.
 */
export function formatRevisionRemarksForDisplay(raw: string | null | undefined): string {
  if (!raw || typeof raw !== "string") return "";
  const parsed = parseRevisionRemarks(raw);
  if (!parsed.hasFieldComments) {
    return parsed.general || raw;
  }

  const lines: string[] = [];
  if (parsed.general && parsed.general.trim().length > 0) {
    lines.push(parsed.general.trim());
  }

  const fieldEntries = Object.entries(parsed.fields);
  if (fieldEntries.length > 0) {
    if (lines.length > 0) lines.push("");
    for (const [key, note] of fieldEntries) {
      const meta = REVISION_SUPPORTED_FIELDS.find((f) => f.key === key);
      const label = meta ? meta.label : key;
      lines.push(`• ${label}: ${note}`);
    }
  }

  return lines.join("\n");
}
