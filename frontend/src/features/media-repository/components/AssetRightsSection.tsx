import { useEffect, useState } from "react";
import {
  getAssetRights,
  putAssetRights,
  type MediaAssetRights,
  type RightsBasis,
  type RightsState,
} from "../../../api/mediaApi";
import { useToast } from "../../../context/ToastContext";

interface AssetRightsSectionProps {
  assetId: string;
}

const BASIS_OPTIONS: { value: RightsBasis; label: string }[] = [
  { value: "owned", label: "Owned by institution" },
  { value: "consent", label: "Consent on file" },
  { value: "licensed", label: "Licensed" },
  { value: "public_domain", label: "Public domain" },
  { value: "unknown", label: "Unknown" },
];

const STATE_META: Record<RightsState, { label: string; tone: string }> = {
  cleared: { label: "Cleared", tone: "good" },
  expiring_soon: { label: "Expiring soon", tone: "warn" },
  expired: { label: "Expired", tone: "danger" },
  incomplete: { label: "Incomplete", tone: "muted" },
};

interface FormState {
  rightsHolder: string;
  rightsBasis: RightsBasis;
  licenseUri: string;
  consentReference: string;
  clearanceDate: string;
  expiresAt: string;
  permittedChannels: string;
  restrictions: string;
  supportingDocumentUrl: string;
}

function toForm(r: MediaAssetRights): FormState {
  return {
    rightsHolder: r.rightsHolder ?? "",
    rightsBasis: r.rightsBasis ?? "unknown",
    licenseUri: r.licenseUri ?? "",
    consentReference: r.consentReference ?? "",
    clearanceDate: r.clearanceDate ?? "",
    expiresAt: r.expiresAt ?? "",
    permittedChannels: (r.permittedChannels ?? []).join(", "),
    restrictions: r.restrictions ?? "",
    supportingDocumentUrl: r.supportingDocumentUrl ?? "",
  };
}

/**
 * UC-4.12 Phase 7D: self-contained rights/consent editor for the shared Asset Detail sidebar.
 * Loads and saves its own data by assetId so neither host screen has to thread rights props.
 */
export default function AssetRightsSection({ assetId }: AssetRightsSectionProps) {
  const toast = useToast();
  const [rights, setRights] = useState<MediaAssetRights | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<FormState | null>(null);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => {
      if (!active) return;
      setLoading(true);
      setEditing(false);
      getAssetRights(assetId)
        .then((r) => {
          if (active) setRights(r);
        })
        .catch(() => {
          if (active) setRights(null);
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    });
    return () => {
      active = false;
    };
  }, [assetId]);

  function startEdit() {
    if (rights) setForm(toForm(rights));
    setEditing(true);
  }

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => (prev ? { ...prev, [key]: value } : prev));
  }

  async function save() {
    if (!form) return;
    setSaving(true);
    try {
      const saved = await putAssetRights(assetId, {
        rightsHolder: form.rightsHolder.trim() || null,
        rightsBasis: form.rightsBasis,
        licenseUri: form.licenseUri.trim() || null,
        consentReference: form.consentReference.trim() || null,
        clearanceDate: form.clearanceDate || null,
        expiresAt: form.expiresAt || null,
        permittedChannels: form.permittedChannels
          .split(",")
          .map((c) => c.trim())
          .filter(Boolean),
        restrictions: form.restrictions.trim() || null,
        supportingDocumentUrl: form.supportingDocumentUrl.trim() || null,
      });
      setRights(saved);
      setEditing(false);
      toast.success("Rights record saved.");
    } catch (err: unknown) {
      const message =
        (err as { response?: { data?: { error?: string; message?: string } } })?.response?.data
          ?.error ?? "Could not save the rights record.";
      toast.error(message);
    } finally {
      setSaving(false);
    }
  }

  const state = rights?.derivedState ?? "incomplete";
  const meta = STATE_META[state];

  return (
    <div className="med-panel-section">
      <div className="med-panel-section-label">Rights &amp; Consent</div>

      {loading ? (
        <p className="med-rights-muted">Loading rights…</p>
      ) : editing && form ? (
        <div className="med-rights-form">
          <label className="med-rights-field">
            <span>Rights holder</span>
            <input
              type="text"
              value={form.rightsHolder}
              onChange={(e) => update("rightsHolder", e.target.value)}
              placeholder="e.g. DOST Region 7"
            />
          </label>
          <label className="med-rights-field">
            <span>Basis</span>
            <select value={form.rightsBasis} onChange={(e) => update("rightsBasis", e.target.value as RightsBasis)}>
              {BASIS_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <div className="med-rights-row">
            <label className="med-rights-field">
              <span>Cleared on</span>
              <input type="date" value={form.clearanceDate} onChange={(e) => update("clearanceDate", e.target.value)} />
            </label>
            <label className="med-rights-field">
              <span>Expires</span>
              <input type="date" value={form.expiresAt} onChange={(e) => update("expiresAt", e.target.value)} />
            </label>
          </div>
          <label className="med-rights-field">
            <span>Permitted channels</span>
            <input
              type="text"
              value={form.permittedChannels}
              onChange={(e) => update("permittedChannels", e.target.value)}
              placeholder="facebook, website (comma-separated)"
            />
          </label>
          <label className="med-rights-field">
            <span>License URI</span>
            <input type="text" value={form.licenseUri} onChange={(e) => update("licenseUri", e.target.value)} />
          </label>
          <label className="med-rights-field">
            <span>Consent reference</span>
            <input
              type="text"
              value={form.consentReference}
              onChange={(e) => update("consentReference", e.target.value)}
            />
          </label>
          <label className="med-rights-field">
            <span>Restrictions</span>
            <textarea
              rows={2}
              value={form.restrictions}
              onChange={(e) => update("restrictions", e.target.value)}
            />
          </label>
          <label className="med-rights-field">
            <span>Supporting document URL</span>
            <input
              type="text"
              value={form.supportingDocumentUrl}
              onChange={(e) => update("supportingDocumentUrl", e.target.value)}
            />
          </label>
          <div className="med-rights-actions">
            <button type="button" className="med-btn med-btn-ghost med-btn-sm" onClick={() => setEditing(false)} disabled={saving}>
              Cancel
            </button>
            <button type="button" className="med-btn med-btn-primary med-btn-sm" onClick={() => void save()} disabled={saving}>
              {saving ? "Saving…" : "Save rights"}
            </button>
          </div>
        </div>
      ) : (
        <div className="med-rights-view">
          <div className="med-rights-head">
            <span className={`med-rights-badge ${meta.tone}`}>{meta.label}</span>
            {rights?.permitsPublicClearance && (
              <span className="med-rights-eligible">Eligible for public clearance</span>
            )}
          </div>
          {rights?.present ? (
            <dl className="med-rights-list">
              <div>
                <dt>Holder</dt>
                <dd>{rights.rightsHolder || "—"}</dd>
              </div>
              <div>
                <dt>Basis</dt>
                <dd>{BASIS_OPTIONS.find((o) => o.value === rights.rightsBasis)?.label ?? rights.rightsBasis}</dd>
              </div>
              {rights.expiresAt && (
                <div>
                  <dt>Expires</dt>
                  <dd>{rights.expiresAt}</dd>
                </div>
              )}
              {rights.permittedChannels.length > 0 && (
                <div>
                  <dt>Channels</dt>
                  <dd>{rights.permittedChannels.join(", ")}</dd>
                </div>
              )}
              {rights.restrictions && (
                <div>
                  <dt>Restrictions</dt>
                  <dd>{rights.restrictions}</dd>
                </div>
              )}
            </dl>
          ) : (
            <p className="med-rights-muted">
              No rights record yet. A complete, non-expired record is required before a new asset can
              be cleared for public use.
            </p>
          )}
          <button type="button" className="med-btn med-btn-ghost med-btn-sm" onClick={startEdit}>
            {rights?.present ? "Edit rights" : "Add rights record"}
          </button>
        </div>
      )}
    </div>
  );
}
