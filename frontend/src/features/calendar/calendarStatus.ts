export const STATUS_COLORS: Record<string, { bg: string; text: string }> = {
  scheduled: { bg: "#eaf2ff", text: "#1d4ed8" },
  published: { bg: "#ecfdf5", text: "#047857" },
  published_manual: { bg: "#dcfce7", text: "#166534" },
  publish_failed: { bg: "#fef2f2", text: "#b91c1c" },
  direct_post_failed: { bg: "#fef2f2", text: "#b91c1c" },
  admin_direct_post: { bg: "#f5f3ff", text: "#6d28d9" },
  direct_post_scheduled: { bg: "#f5f3ff", text: "#6d28d9" },
  pending: { bg: "#fff7ed", text: "#c2410c" },
  in_review: { bg: "#fff7ed", text: "#c2410c" },
  needs_revision: { bg: "#fff7ed", text: "#c2410c" },
  rejected: { bg: "#fef2f2", text: "#b91c1c" },
};

export const STATUS_LABELS: Record<string, string> = {
  scheduled: "Scheduled",
  published: "Published",
  published_manual: "Manual Publish",
  publish_failed: "Failed",
  direct_post_failed: "Failed",
  admin_direct_post: "Admin Post",
  direct_post_scheduled: "Admin Post",
  pending: "Needs Attention",
  in_review: "Needs Attention",
  needs_revision: "Needs Attention",
  rejected: "Rejected",
};

export function statusColor(status: string) {
  return STATUS_COLORS[status.toLowerCase()] ?? { bg: "#eef2f7", text: "#475569" };
}

export function statusLabel(status: string) {
  return STATUS_LABELS[status.toLowerCase()] ?? status;
}
