import { useEffect, useState } from "react";
import type { User } from "../../types/auth.types";
import { changePassword, getMe, getPageSettings, listInstitutions, updateAccountSettings, updatePageSettings } from "../../api/authApi";
import { createMessengerLinkCode, disconnectMessenger, getMessengerConnectionStatus, type MessengerConnection, type MessengerLinkCode } from "../../api/messengerApi";
import { useToast } from "../../context/ToastContext";

interface Props { user: User; onProfileUpdated: () => Promise<void>; }

export default function AccountSettingsScreen({ user, onProfileUpdated }: Props) {
  const toast = useToast();
  const [displayName, setDisplayName] = useState(user.displayName || user.name);
  const [notifyInApp, setNotifyInApp] = useState(true);
  const [notifyEmail, setNotifyEmail] = useState(true);
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [watermarkEnabled, setWatermarkEnabled] = useState(false);
  const [watermarkText, setWatermarkText] = useState("");
  const [facebookPageId, setFacebookPageId] = useState("");
  const [institutions, setInstitutions] = useState<{ id: string; name: string }[]>([]);
  const [selectedInstitutionId, setSelectedInstitutionId] = useState("");
  const [saving, setSaving] = useState<"account" | "password" | "page" | "messenger" | null>(null);
  
  // Messenger integration state
  const [messengerStatus, setMessengerStatus] = useState<MessengerConnection | null>(null);
  const [linkCode, setLinkCode] = useState<MessengerLinkCode | null>(null);
  const [copiedCode, setCopiedCode] = useState(false);

  const canManagePage = user.role !== "contributor";
  const pageInstitutionId = user.role === "administrator" ? user.institutionId : selectedInstitutionId || null;

  const loadMessenger = () => {
    if (!canManagePage) return;
    getMessengerConnectionStatus()
      .then((data) => setMessengerStatus(data))
      .catch(() => setMessengerStatus(null));
  };

  useEffect(() => {
    void getMe().then(({ data }) => {
      setDisplayName(data.displayName || "");
      setNotifyInApp(data.notifyInApp);
      setNotifyEmail(data.notifyEmail);
    });
    if (canManagePage) {
      loadMessenger();
      void getPageSettings(pageInstitutionId).then(({ data }) => {
        setWatermarkEnabled(data.watermarkEnabled);
        setWatermarkText(data.watermarkText || "");
        setFacebookPageId(data.facebookPageId || "");
      }).catch(() => toast.error("Unable to load Page Settings."));
    }
    if (user.role === "super_administrator") void listInstitutions().then(({ data }) =>
      setInstitutions(data.map((item) => ({ id: item.id, name: item.name }))));
  }, [canManagePage, pageInstitutionId, toast, user.role]);

  async function saveAccount() {
    setSaving("account");
    try {
      await updateAccountSettings({ displayName, notifyInApp, notifyEmail });
      await onProfileUpdated();
      toast.success("Account settings updated.");
    } catch { toast.error("Unable to update account settings."); }
    finally { setSaving(null); }
  }

  async function savePassword() {
    if (newPassword.length < 8) return toast.error("New password must be at least 8 characters.");
    setSaving("password");
    try {
      await changePassword(currentPassword, newPassword);
      setCurrentPassword(""); setNewPassword("");
      toast.success("Password changed. Other signed-in devices remain active.");
    } catch { toast.error("Password change failed. Check your current password."); }
    finally { setSaving(null); }
  }

  async function savePage() {
    setSaving("page");
    try {
      await updatePageSettings({ watermarkEnabled, watermarkText, facebookPageId }, pageInstitutionId);
      toast.success("Page settings updated.");
    } catch { toast.error("Unable to update Page Settings."); }
    finally { setSaving(null); }
  }

  async function generateMessengerCode() {
    setSaving("messenger");
    try {
      const res = await createMessengerLinkCode();
      setLinkCode(res);
      setCopiedCode(false);
      toast.success("Messenger link code generated. Send it to the official Page.");
    } catch {
      toast.error("Unable to generate Messenger link code.");
    } finally {
      setSaving(null);
    }
  }

  async function handleDisconnectMessenger() {
    if (!window.confirm("Are you sure you want to disconnect Facebook Messenger alerts?")) return;
    setSaving("messenger");
    try {
      await disconnectMessenger();
      setMessengerStatus({ connected: false, enabled: false, linkedAt: null });
      setLinkCode(null);
      toast.success("Facebook Messenger disconnected.");
    } catch {
      toast.error("Unable to disconnect Messenger.");
    } finally {
      setSaving(null);
    }
  }

  function handleCopyCode() {
    if (!linkCode) return;
    void navigator.clipboard.writeText(linkCode.code).then(() => {
      setCopiedCode(true);
      toast.success("Code copied to clipboard!");
      setTimeout(() => setCopiedCode(false), 3000);
    });
  }

  return <div className="dash-body settings-page">
    <header className="settings-page-header">
      <div><div className="dash-greeting">Settings</div><p className="settings-page-subtitle">Manage your account, security, and publishing preferences.</p></div>
      <span className="settings-role-badge"><i className="ti ti-shield-check" />{formatRole(user.role)}</span>
    </header>

    <div className="settings-grid">
      <section className="settings-card" id="account">
        <SettingsHeader icon="ti ti-user-circle" title="Account Settings" description="Personalize your profile and notification delivery." />
        <div className="settings-card-body">
          <div className="settings-field">
            <label htmlFor="settings-display-name">Display name</label>
            <input id="settings-display-name" className="settings-input" value={displayName} maxLength={150} placeholder="Enter your display name" onChange={(e) => setDisplayName(e.target.value)} />
            <span className="settings-field-hint">This name appears across your DASIGConnect workspace.</span>
          </div>
          <div className="settings-field">
            <span className="settings-label">Notification preferences</span>
            <div className="settings-toggle-list">
              <Toggle icon="ti ti-bell" title="In-app notifications" description="Receive workflow updates inside DASIGConnect." checked={notifyInApp} onChange={setNotifyInApp} />
              <Toggle icon="ti ti-mail" title="Email notifications" description="Receive important activity and account notices by email." checked={notifyEmail} onChange={setNotifyEmail} />
            </div>
          </div>
        </div>
        <SettingsFooter label="Save Account Settings" icon="ti ti-device-floppy" busy={saving === "account"} onClick={() => void saveAccount()} />
      </section>

      <section className="settings-card" id="password">
        <SettingsHeader icon="ti ti-lock" title="Password & Security" description="Use a strong password to protect your account." />
        <div className="settings-card-body settings-password-grid">
          <div className="settings-field"><label htmlFor="settings-current-password">Current password</label>
            <input id="settings-current-password" className="settings-input" type="password" autoComplete="current-password" value={currentPassword} placeholder="Enter current password" onChange={(e) => setCurrentPassword(e.target.value)} />
          </div>
          <div className="settings-field"><label htmlFor="settings-new-password">New password</label>
            <input id="settings-new-password" className="settings-input" type="password" autoComplete="new-password" value={newPassword} placeholder="At least 8 characters" onChange={(e) => setNewPassword(e.target.value)} />
            <span className="settings-field-hint">Changing it here keeps your other signed-in sessions active.</span>
          </div>
        </div>
        <SettingsFooter label="Change Password" icon="ti ti-key" busy={saving === "password"} disabled={!currentPassword || !newPassword} onClick={() => void savePassword()} />
      </section>

      {canManagePage && <section className="settings-card settings-card-wide">
        <SettingsHeader icon="ti ti-adjustments-horizontal" title="Page Settings" description="Configure publishing identity and watermark defaults for the selected scope."
          accent={<span className="settings-admin-chip"><i className="ti ti-crown" />Authorized access</span>} />
        <div className="settings-card-body">
          {user.role === "super_administrator" && <div className="settings-scope-panel">
            <div className="settings-scope-icon"><i className="ti ti-building-community" /></div>
            <div className="settings-scope-copy"><label htmlFor="settings-scope">Configuration scope</label><span>Choose network defaults or override one institution.</span></div>
            <select id="settings-scope" className="settings-select" value={selectedInstitutionId} onChange={(e) => setSelectedInstitutionId(e.target.value)}>
              <option value="">Network-wide defaults</option>
              {institutions.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          </div>}
          <div className="settings-page-columns">
            <div className="settings-subsection">
              <SubsectionHeader icon="ti ti-droplet" tone="purple" title="Watermark Configuration" description="Apply a consistent identity to published media." />
              <Toggle title="Enable watermark" description="Apply the configured watermark to eligible media." checked={watermarkEnabled} onChange={setWatermarkEnabled} />
              <div className="settings-field"><label htmlFor="settings-watermark-text">Watermark text</label>
                <input id="settings-watermark-text" className="settings-input" value={watermarkText} maxLength={150} disabled={!watermarkEnabled} placeholder="e.g. DASIGConnect" onChange={(e) => setWatermarkText(e.target.value)} />
              </div>
            </div>
            <div className="settings-subsection">
              <SubsectionHeader icon="ti ti-brand-facebook" tone="blue" title="Facebook Integration" description="Identify the Facebook Page used for publishing." />
              <div className="settings-field"><label htmlFor="settings-facebook-id">Facebook Page ID</label>
                <div className="settings-input-with-icon"><i className="ti ti-brand-facebook" /><input id="settings-facebook-id" className="settings-input" value={facebookPageId} maxLength={255} placeholder="Enter Facebook Page ID" onChange={(e) => setFacebookPageId(e.target.value)} /></div>
                <span className="settings-field-hint">Access tokens remain secured separately.</span>
              </div>
            </div>
          </div>
        </div>
        <SettingsFooter label="Save Page Settings" icon="ti ti-device-floppy" busy={saving === "page"} onClick={() => void savePage()} />
      </section>}

      {canManagePage && <section className="settings-card settings-card-wide" id="messenger">
        <SettingsHeader
          icon="ti ti-brand-messenger"
          title="Facebook Messenger Alerts"
          description="Receive instant alerts for submissions, schedule warnings, and critical publishing events directly on Messenger."
          accent={messengerStatus?.connected
            ? <span className="settings-admin-chip" style={{ background: "#dcfce7", color: "#166534" }}><i className="ti ti-circle-check" />Connected</span>
            : <span className="settings-admin-chip"><i className="ti ti-plug" />Integration</span>}
        />
        <div className="settings-card-body">
          {messengerStatus?.connected ? (
            <div className="settings-messenger-connected">
              <div className="settings-messenger-badge">
                <span className="settings-messenger-status-dot" />
                <div className="settings-messenger-badge-text">
                  <strong>Messenger Account Linked & Active</strong>
                  <span>Connected {messengerStatus.linkedAt ? new Date(messengerStatus.linkedAt).toLocaleDateString() : ""} — Real-time alerts enabled</span>
                </div>
              </div>
              <button
                type="button"
                className="settings-messenger-disconnect-btn"
                disabled={saving === "messenger"}
                onClick={() => void handleDisconnectMessenger()}
              >
                <i className="ti ti-plug-connected-x" /> Disconnect
              </button>
            </div>
          ) : (
            <div className="settings-messenger-box">
              <div className="settings-messenger-steps">
                <p>Link your personal Facebook Messenger to receive automated real-time alerts (T-01, T-06, T-11) directly from DASIGConnect.</p>
                <ol>
                  <li>Click <strong>Generate Link Code</strong> below to receive a secure 10-minute code.</li>
                  <li>Open Facebook Messenger and send the exact command to the official DASIGConnect Page.</li>
                  <li>DASIGConnect will verify the code and immediately confirm your connection.</li>
                </ol>
              </div>

              {linkCode ? (
                <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                  <div className="settings-messenger-code-container">
                    <span>{linkCode.code}</span>
                    <button type="button" className="settings-messenger-copy-btn" onClick={handleCopyCode}>
                      <i className={copiedCode ? "ti ti-check" : "ti ti-copy"} />
                      {copiedCode ? "Copied" : "Copy"}
                    </button>
                  </div>
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: "11px", color: "var(--d-muted)" }}>
                    <span>Expires in 10 minutes</span>
                    <button
                      type="button"
                      style={{ background: "none", border: "none", color: "var(--d-blue)", cursor: "pointer", textDecoration: "underline", fontSize: "11px" }}
                      onClick={loadMessenger}
                    >
                      Check Connection Status
                    </button>
                  </div>
                </div>
              ) : (
                <div>
                  <button
                    type="button"
                    className="settings-save-button"
                    style={{ display: "inline-flex", alignItems: "center", gap: "8px", width: "auto" }}
                    disabled={saving === "messenger"}
                    onClick={() => void generateMessengerCode()}
                  >
                    <i className="ti ti-key" /> Generate Link Code
                  </button>
                </div>
              )}
            </div>
          )}
        </div>
      </section>}
    </div>
  </div>;
}

function SettingsHeader({ icon, title, description, accent }: { icon: string; title: string; description: string; accent?: React.ReactNode }) {
  return <header className="settings-card-header"><span className="settings-card-icon"><i className={icon} /></span><div className="settings-card-heading"><h2>{title}</h2><p>{description}</p></div>{accent}</header>;
}
function SubsectionHeader({ icon, tone, title, description }: { icon: string; tone: string; title: string; description: string }) {
  return <div className="settings-subsection-heading"><span className={`settings-subsection-icon settings-icon-${tone}`}><i className={icon} /></span><div><h3>{title}</h3><p>{description}</p></div></div>;
}
function SettingsFooter({ label, icon, busy, disabled, onClick }: { label: string; icon: string; busy: boolean; disabled?: boolean; onClick: () => void }) {
  return <footer className="settings-card-footer"><button type="button" className="settings-save-button" disabled={busy || disabled} aria-busy={busy} onClick={onClick}><i className={busy ? "ti ti-loader-2 settings-spinner" : icon} />{busy ? "Saving…" : label}</button></footer>;
}
function Toggle({ icon, title, description, checked, onChange }: { icon?: string; title: string; description: string; checked: boolean; onChange: (checked: boolean) => void }) {
  return <label className="settings-toggle-row">{icon && <span className="settings-toggle-icon"><i className={icon} /></span>}<span className="settings-toggle-copy"><strong>{title}</strong><span>{description}</span></span><input className="settings-toggle-input" type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} /><span className="settings-switch"><span /></span></label>;
}
function formatRole(role: User["role"]) { return role === "super_administrator" ? "Super Administrator" : role === "administrator" ? "Administrator" : "Contributor"; }
