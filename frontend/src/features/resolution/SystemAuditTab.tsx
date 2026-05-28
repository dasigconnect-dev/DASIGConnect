import { useCallback, useEffect, useRef, useState } from "react";
import { getTokenStatuses, initOAuth, type TokenStatus } from "../../api/resolutionApi";
import { useToast } from "../../context/ToastContext";

function formatDate(iso: string | null) {
  if (!iso) return "—";
  return new Date(iso).toLocaleString("en-PH", {
    month: "short", day: "numeric", year: "numeric",
    hour: "numeric", minute: "2-digit", hour12: true,
  });
}

function daysUntilExpiry(iso: string | null): string {
  if (!iso) return "—";
  const diff = new Date(iso).getTime() - Date.now();
  if (diff <= 0) return "Expired";
  const days = Math.floor(diff / 86_400_000);
  if (days === 0) return "Today";
  return `${days}d`;
}

interface TokenStatusBadgeProps { status: TokenStatus["tokenStatus"] }
function TokenStatusBadge({ status }: TokenStatusBadgeProps) {
  const map: Record<TokenStatus["tokenStatus"], { cls: string; label: string }> = {
    ACTIVE:   { cls: "rc-tok-active",   label: "Active" },
    EXPIRING: { cls: "rc-tok-expiring", label: "Expiring Soon" },
    EXPIRED:  { cls: "rc-tok-expired",  label: "Expired" },
    INVALID:  { cls: "rc-tok-invalid",  label: "Invalid" },
  };
  const { cls, label } = map[status];
  return <span className={`rc-tok-badge ${cls}`}>{label}</span>;
}

interface Props {
  refreshSignal: number;
  onIssueCount: (n: number) => void;
  tokenSectionRef?: React.RefObject<HTMLElement | null>;
}

export default function SystemAuditTab({ refreshSignal, onIssueCount, tokenSectionRef }: Props) {
  const toast = useToast();
  const [tokens, setTokens] = useState<TokenStatus[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busyToken, setBusyToken] = useState<string | null>(null);
  const localRef = useRef<HTMLElement | null>(null);
  const sectionRef = tokenSectionRef ?? localRef;

  const load = useCallback((signal?: AbortSignal) => {
    setLoading(true);
    setError("");
    getTokenStatuses(signal)
      .then((res) => {
        setTokens(res.data);
        const issues = res.data.filter((t) => t.tokenStatus !== "ACTIVE").length;
        onIssueCount(issues);
      })
      .catch((err: unknown) => {
        if ((err as { name?: string }).name === "CanceledError") return;
        setError("Could not load token statuses.");
      })
      .finally(() => setLoading(false));
  }, [onIssueCount]);

  useEffect(() => {
    const controller = new AbortController();
    queueMicrotask(() => load(controller.signal));
    return () => controller.abort();
  }, [load, refreshSignal]);

  async function handleReauth(token: TokenStatus) {
    setBusyToken(token.id);
    try {
      const res = await initOAuth(token.id);
      const url = res.data.authorizationUrl;
      if (url) {
        window.open(url, "_blank", "noopener,noreferrer");
        toast.info("OAuth flow opened in a new tab. Return here after authorizing to confirm.");
      } else {
        toast.error("Could not retrieve authorization URL.");
      }
    } catch {
      toast.error("Re-authentication failed. Please try again.");
    } finally {
      setBusyToken(null);
    }
  }

  return (
    <div className="rc-system-audit">
      {/* ── Token Management ── */}
      <section ref={sectionRef} className="rc-section">
        <div className="rc-section-header">
          <div>
            <h2 className="rc-section-title">
              <i className="ti ti-key" aria-hidden="true" />
              Token Management
            </h2>
            <p className="rc-section-sub">
              Facebook Page Access Token status for all institutions. Tokens are
              encrypted at rest (AES-256-GCM) and values are masked.
            </p>
          </div>
          <button
            type="button"
            className="btn-secondary rc-section-refresh"
            onClick={() => load()}
            disabled={loading}
          >
            <i className="ti ti-refresh" aria-hidden="true" />
            Refresh
          </button>
        </div>

        {loading && (
          <div className="rc-tab-state">
            <div className="spinner-ring" />
            <span>Loading token statuses...</span>
          </div>
        )}

        {!loading && error && (
          <div className="rc-tab-state rc-tab-state-error">
            <i className="ti ti-alert-circle" aria-hidden="true" />
            <span>{error}</span>
            <button type="button" className="btn-secondary" onClick={() => load()}>Retry</button>
          </div>
        )}

        {!loading && !error && tokens.length === 0 && (
          <div className="rc-tab-state rc-tab-state-empty">
            <i className="ti ti-brand-facebook" aria-hidden="true" />
            <span>No Facebook tokens configured</span>
            <p>Tokens are provisioned when an institution's Facebook integration is set up.</p>
          </div>
        )}

        {!loading && !error && tokens.length > 0 && (
          <div className="rc-table-wrap">
            <table className="rc-table">
              <thead>
                <tr>
                  <th>Page ID</th>
                  <th>Token Status</th>
                  <th>Expires</th>
                  <th>Last Validated</th>
                  <th className="rc-th-actions">Action</th>
                </tr>
              </thead>
              <tbody>
                {tokens.map((token) => {
                  const isBusy = busyToken === token.id;
                  const needsAction = token.tokenStatus !== "ACTIVE";
                  return (
                    <tr key={token.id} className={`rc-tr${needsAction ? " rc-tr-attention" : ""}`}>
                      <td className="rc-td-mono">{token.pageId}</td>
                      <td><TokenStatusBadge status={token.tokenStatus} /></td>
                      <td>
                        <span className={token.tokenStatus === "EXPIRED" ? "rc-text-danger" : ""}>
                          {formatDate(token.expiresAt)}
                          {token.expiresAt && (
                            <span className="rc-expiry-delta"> ({daysUntilExpiry(token.expiresAt)})</span>
                          )}
                        </span>
                      </td>
                      <td className="rc-muted">{formatDate(token.lastValidatedAt)}</td>
                      <td className="rc-td-actions">
                        <button
                          type="button"
                          className={`rc-act-btn ${needsAction ? "rc-act-approve" : "rc-act-suggest"}`}
                          disabled={isBusy}
                          onClick={() => void handleReauth(token)}
                          title="Re-authenticate via OAuth 2.0"
                        >
                          {isBusy ? (
                            <div className="spinner-ring spinner-ring-xs" />
                          ) : (
                            <i className="ti ti-brand-facebook" aria-hidden="true" />
                          )}
                          Re-Authenticate
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}

        <div className="rc-tok-info">
          <i className="ti ti-info-circle" aria-hidden="true" />
          <span>
            Clicking <strong>Re-Authenticate</strong> opens the Meta OAuth flow in a new tab.
            After authorizing, the server automatically exchanges the code for a long-lived
            Page Access Token. Daily health checks and automated publishing resume immediately
            upon successful reauthorization.
          </span>
        </div>
      </section>

      {/* ── Audit Log ── */}
      <section className="rc-section rc-section-divider">
        <div className="rc-section-header">
          <div>
            <h2 className="rc-section-title">
              <i className="ti ti-clipboard-list" aria-hidden="true" />
              Exception-Handling Audit Log
            </h2>
            <p className="rc-section-sub">
              Immutable record of all Administrator exception-handling actions.
              Entries cannot be edited or deleted per Section 3.4.5 of the SDD.
            </p>
          </div>
        </div>

        <div className="rc-audit-placeholder">
          <i className="ti ti-database" aria-hidden="true" />
          <span>Audit log viewer</span>
          <p>
            The filterable audit log endpoint is available from the Analytics Dashboard
            under Administrator workload metrics, or directly from the backend at{" "}
            <code>/api/v1/audit-log</code>.
          </p>
        </div>
      </section>
    </div>
  );
}
