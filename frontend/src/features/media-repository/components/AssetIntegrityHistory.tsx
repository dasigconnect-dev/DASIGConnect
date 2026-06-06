import { CircleAlert, CircleCheck, Clock3 } from "lucide-react";
import type { MediaIntegrityCheck } from "../../../api/mediaApi";
import { formatUploadDate } from "../utils";

interface AssetIntegrityHistoryProps {
  entries: MediaIntegrityCheck[];
  loading: boolean;
}

export default function AssetIntegrityHistory({
  entries,
  loading,
}: AssetIntegrityHistoryProps) {
  if (loading) {
    return <p className="med-integrity-history-empty">Loading verification history...</p>;
  }
  if (entries.length === 0) {
    return <p className="med-integrity-history-empty">No verification records yet.</p>;
  }

  return (
    <ol className="med-integrity-history">
      {entries.slice(0, 4).map((entry) => {
        const Icon = entry.status === "verified" ? CircleCheck
          : entry.status === "pending" ? Clock3 : CircleAlert;
        return (
          <li key={entry.id}>
            <Icon size={13} aria-hidden="true" />
            <span>
              <strong>{statusLabel(entry.status)}</strong>
              <small>
                {triggerLabel(entry.trigger)} · {formatUploadDate(entry.checkedAt)}
              </small>
            </span>
          </li>
        );
      })}
    </ol>
  );
}

function statusLabel(status: MediaIntegrityCheck["status"]) {
  switch (status) {
    case "verified": return "Verified";
    case "mismatch": return "Checksum mismatch";
    case "missing": return "File missing";
    case "error": return "Verification error";
    default: return "Pending";
  }
}

function triggerLabel(trigger: MediaIntegrityCheck["trigger"]) {
  switch (trigger) {
    case "ingest": return "Upload check";
    case "scheduled": return "Scheduled check";
    case "repair_verification": return "Repair check";
    default: return "Manual check";
  }
}
