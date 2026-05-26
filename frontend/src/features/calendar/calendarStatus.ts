export const STATUS_COLORS: Record<string, { bg: string; text: string }> = {
  scheduled: { bg: "#2563eb", text: "#fff" },
  published: { bg: "#16a34a", text: "#fff" },
  published_manual: { bg: "#15803d", text: "#fff" },
  publish_failed: { bg: "#dc2626", text: "#fff" },
  admin_direct_post: { bg: "#7c3aed", text: "#fff" },
};

export const STATUS_LABELS: Record<string, string> = {
  scheduled: "Scheduled",
  published: "Published",
  published_manual: "Published (Manual)",
  publish_failed: "Failed",
  admin_direct_post: "Admin Post",
};

export function statusColor(status: string) {
  return STATUS_COLORS[status.toLowerCase()] ?? { bg: "#64748b", text: "#fff" };
}

export function statusLabel(status: string) {
  return STATUS_LABELS[status.toLowerCase()] ?? status;
}
