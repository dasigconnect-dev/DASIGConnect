import "../../styles/dasig-loader.css";
import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import type { User } from "../../types/auth.types";
import type { WatermarkElement } from "../../types/watermark.types";
import { changePassword, getMe, getPageSettings, updateAccountSettings, updatePageSettings } from "../../api/authApi";
import { createMessengerLinkCode, disconnectMessenger, getMessengerConnectionStatus, type MessengerConnection, type MessengerLinkCode } from "../../api/messengerApi";
import { getWatermarkConfiguration, saveWatermarkConfiguration } from "../../api/watermarkApi";
import WatermarkCanvasEditor from "../settings/components/WatermarkCanvasEditor";
import { useToast } from "../../context/ToastContext";
import { firstPasswordError, getPasswordRules } from "../../lib/passwordPolicy";

interface Props {
  user: User;
  onProfileUpdated: () => Promise<void>;
}

type SettingsTab = "account" | "password" | "page";

export default function AccountSettingsScreen({ user, onProfileUpdated }: Props) {
  const toast = useToast();
  const location = useLocation();
  const navigate = useNavigate();

  const canManagePage = user.role === "admin";
  const [initialLoading, setInitialLoading] = useState(true);

  // Tab State
  const [activeTab, setActiveTab] = useState<SettingsTab>(() => {
    const hash = window.location.hash.replace("#", "");
    if (hash === "password") return "password";
    if (hash === "page" && canManagePage) return "page";
    return "account";
  });

  const [displayName, setDisplayName] = useState(user.displayName || user.name);
  const [initialDisplayName, setInitialDisplayName] = useState(user.displayName || user.name);
  const [notifyInApp, setNotifyInApp] = useState(true);
  const [initialNotifyInApp, setInitialNotifyInApp] = useState(true);
  const [notifyEmail, setNotifyEmail] = useState(true);
  const [initialNotifyEmail, setInitialNotifyEmail] = useState(true);
  const [currentPassword, setCurrentPassword] = useState("");
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [newPassword, setNewPassword] = useState("");
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [facebookPageId, setFacebookPageId] = useState("");

  // Watermark Studio States
  const [watermarkEnabled, setWatermarkEnabled] = useState(true);
  const [watermarkElements, setWatermarkElements] = useState<WatermarkElement[]>([]);
  const [watermarkLoading, setWatermarkLoading] = useState(false);

  // Messenger Integration States
  const [messengerStatus, setMessengerStatus] = useState<MessengerConnection | null>(null);
  const [linkCode, setLinkCode] = useState<MessengerLinkCode | null>(null);
  const [copiedCode, setCopiedCode] = useState(false);

  const [saving, setSaving] = useState<"account" | "password" | "page" | "watermark" | "messenger" | null>(null);
  const pageInstitutionId = null;
  const newPasswordRules = getPasswordRules(newPassword, [
    user.email,
    user.name,
    user.displayName,
  ]);
  const newPasswordOk = Object.values(newPasswordRules).every(Boolean);

  // Display Name Validation
  function validateDisplayName(name: string) {
    const trimmed = name.trim();
    if (!trimmed) return "Display name cannot be empty.";
    if (trimmed.length < 2) return "Display name must be at least 2 characters.";
    if (trimmed.length > 60) return "Display name cannot exceed 60 characters.";
    const validPattern = /^[a-zA-Z0-9\s\-'.ñÑ]+$/;
    if (!validPattern.test(name)) {
      return "Only letters, numbers, spaces, hyphens, periods, and apostrophes are allowed.";
    }
    return null;
  }

  const nameError = validateDisplayName(displayName);
  const isNameValid = !nameError;
  const isAccountChanged =
    displayName.trim() !== initialDisplayName.trim() ||
    notifyInApp !== initialNotifyInApp ||
    notifyEmail !== initialNotifyEmail;

  // Studio sub-view state
  const [isStudioOpen, setIsStudioOpen] = useState(() => {
    return window.location.hash === "#watermark-studio";
  });

  const loadMessenger = () => {
    if (!canManagePage) return;
    getMessengerConnectionStatus()
      .then((data) => setMessengerStatus(data))
      .catch(() => setMessengerStatus(null));
  };

  useEffect(() => {
    const hash = location.hash.replace("#", "");
    if (hash === "password") {
      setActiveTab("password");
      setIsStudioOpen(false);
    } else if (hash === "watermark-studio" && canManagePage) {
      setActiveTab("page");
      setIsStudioOpen(true);
    } else if (hash === "page" && canManagePage) {
      setActiveTab("page");
      setIsStudioOpen(false);
    } else if (hash === "account") {
      setActiveTab("account");
      setIsStudioOpen(false);
    }
  }, [location.hash, canManagePage]);

  function switchTab(tab: SettingsTab) {
    if (tab === "page" && !canManagePage) return;
    setActiveTab(tab);
    setIsStudioOpen(false);
    navigate(`/settings#${tab}`, { replace: true });
  }

  function openStudio() {
    if (!canManagePage) return;
    setIsStudioOpen(true);
    navigate(`/settings#watermark-studio`, { replace: true });
  }

  function closeStudio() {
    setIsStudioOpen(false);
    navigate(`/settings#${canManagePage ? "page" : "account"}`, { replace: true });
  }

  // Initial mount: load profile and messenger status
  useEffect(() => {
    let isCurrent = true;
    const promises: Promise<unknown>[] = [
      getMe()
        .then(({ data }) => {
          if (!isCurrent) return;
          const fullName = [data.firstName, data.lastName].filter(Boolean).join(" ");
          const name = data.displayName || fullName || user.name || "";
          setDisplayName(name);
          setInitialDisplayName(name);
          setNotifyInApp(data.notifyInApp);
          setInitialNotifyInApp(data.notifyInApp);
          setNotifyEmail(data.notifyEmail);
          setInitialNotifyEmail(data.notifyEmail);
        })
        .catch(() => {}),
    ];

    if (canManagePage) {
      promises.push(
        getMessengerConnectionStatus()
          .then((data) => {
            if (isCurrent) setMessengerStatus(data);
          })
          .catch(() => {
            if (isCurrent) setMessengerStatus(null);
          })
      );
    }

    Promise.allSettled(promises).finally(() => {
      if (isCurrent) {
        setInitialLoading(false);
      }
    });

    return () => {
      isCurrent = false;
    };
  }, [user.name, canManagePage]);

  // Page and Watermark settings loader
  useEffect(() => {
    if (!canManagePage) return;

    let isCurrent = true;
    void getPageSettings(pageInstitutionId)
      .then(({ data }) => {
        if (!isCurrent) return;
        setFacebookPageId(data.facebookPageId || "");
      })
      .catch(() => toast.error("Unable to load Page Settings."));

    setWatermarkLoading(true);
    void getWatermarkConfiguration()
      .then(({ data }) => {
        if (!isCurrent) return;
        setWatermarkEnabled(data.enabled);
        setWatermarkElements(data.elements || []);
      })
      .catch(() => toast.error("Unable to load Watermark configuration."))
      .finally(() => {
        if (isCurrent) setWatermarkLoading(false);
      });

    return () => {
      isCurrent = false;
    };
  }, [canManagePage, pageInstitutionId]);

  async function saveAccount() {
    const cleanName = displayName.trim().replace(/\s+/g, " ");
    const err = validateDisplayName(cleanName);
    if (err) return toast.error(err);

    setSaving("account");
    try {
      await updateAccountSettings({ displayName: cleanName, notifyInApp, notifyEmail });
      setDisplayName(cleanName);
      setInitialDisplayName(cleanName);
      setInitialNotifyInApp(notifyInApp);
      setInitialNotifyEmail(notifyEmail);
      await onProfileUpdated();
      toast.success("Account settings updated.");
    } catch {
      toast.error("Unable to update account settings.");
    } finally {
      setSaving(null);
    }
  }

  function handleRevertDisplayName() {
    setDisplayName(initialDisplayName);
  }

  function handleResetToOfficialName() {
    if (user.name) {
      setDisplayName(user.name);
    }
  }

  async function savePassword() {
    const passwordError = firstPasswordError(newPassword, [
      user.email,
      user.name,
      user.displayName,
    ]);
    if (passwordError) return toast.error(passwordError);
    setSaving("password");
    try {
      await changePassword(currentPassword, newPassword);
      setCurrentPassword("");
      setNewPassword("");
      toast.success("Password changed. Other signed-in devices remain active.");
    } catch {
      toast.error("Password change failed. Check your current password.");
    } finally {
      setSaving(null);
    }
  }

  async function savePage() {
    setSaving("page");
    try {
      await updatePageSettings({ watermarkEnabled, watermarkText: "", facebookPageId }, pageInstitutionId);
      toast.success("Facebook settings updated.");
    } catch {
      toast.error("Unable to update Facebook settings.");
    } finally {
      setSaving(null);
    }
  }

  async function saveWatermark() {
    setSaving("watermark");
    try {
      const { data } = await saveWatermarkConfiguration({
        institutionId: null,
        enabled: watermarkEnabled,
        elements: watermarkElements,
      });
      setWatermarkElements(data.elements || []);
      toast.success("Global watermark configuration saved.");
    } catch (err: unknown) {
      const errorMsg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message;
      toast.error(errorMsg || "Unable to save watermark configuration.");
    } finally {
      setSaving(null);
    }
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

  if (initialLoading) {
    return (
      <div className="dash-body settings-page">
        <header className="settings-page-header">
          <div>
            <div className="dash-greeting">Settings</div>
            <p className="settings-page-subtitle">Manage your account, security, and publishing preferences.</p>
          </div>
          <span className="settings-role-badge">
            <i className="ti ti-shield-check" />
            {formatRole(user.role)}
          </span>
        </header>

        <div
          style={{
            minHeight: "380px",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: "60px 20px",
          }}
        >
          <div className="dc-dot-triangle-container">
            <div className="loader-dots" />
            <div className="dc-dot-triangle-label">
              Loading Settings
              <span className="dc-dot-triangle-label-dots">
                <span className="dc-dot-triangle-dot-char">.</span>
                <span className="dc-dot-triangle-dot-char">.</span>
                <span className="dc-dot-triangle-dot-char">.</span>
              </span>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (isStudioOpen && canManagePage) {
    return (
      <div className="dash-body settings-page settings-studio-fullscreen-page">
        <div className="settings-studio-topbar">
          <button
            type="button"
            className="settings-studio-back-btn"
            onClick={closeStudio}
            title="Return to Page Settings"
          >
            <i className="ti ti-arrow-left" aria-hidden="true" />
            Back to Page Settings
          </button>
        </div>

        <section className="settings-studio-card" id="watermark-studio">
          <header className="settings-studio-header">
            <div className="settings-studio-header-left">
              <div className="settings-studio-title-group">
                <h2>Automated Watermark Studio</h2>
                <p>Design and position elements across square (1:1), portrait (4:5), and landscape (16:9) aspect ratios.</p>
              </div>
            </div>

            <div className="settings-studio-header-right">
              <button
                type="button"
                className="settings-save-button"
                disabled={saving === "watermark" || watermarkLoading}
                onClick={() => void saveWatermark()}
              >
                <i className={saving === "watermark" ? "ti ti-loader-2 settings-spinner" : "ti ti-device-floppy"} />
                {saving === "watermark" ? "Saving..." : "Save Global Watermark"}
              </button>
            </div>
          </header>

          <div className="settings-studio-body">
            {watermarkLoading ? (
              <div style={{ display: "flex", alignItems: "center", justifyContent: "center", padding: "60px", color: "var(--d-muted)" }}>
                <i className="ti ti-loader-2 settings-spinner" style={{ fontSize: "28px", marginRight: "10px" }} />
                Loading Watermark Studio...
              </div>
            ) : (
              <WatermarkCanvasEditor
                elements={watermarkElements}
                onChange={setWatermarkElements}
                disabled={false}
                institutionName="DASIG Central Visayas"
              />
            )}
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="dash-body settings-page">
      <header className="settings-page-header">
        <div>
          <div className="dash-greeting">Settings</div>
          <p className="settings-page-subtitle">Manage your account, security, and publishing preferences.</p>
        </div>
        <span className="settings-role-badge">
          <i className="ti ti-shield-check" />
          {formatRole(user.role)}
        </span>
      </header>

      {/* Main Settings Layout with Sidebar Navigation */}
      <div className="settings-layout">
        {/* Left Side Navigation */}
        <nav className="settings-nav-sidebar" aria-label="Settings categories">
          <div className="sidebar-nav-group">
            <div className="sidebar-nav-label">Preferences</div>
            <button
              type="button"
              className={`sidebar-link ${activeTab === "account" ? "active" : ""}`}
              onClick={() => switchTab("account")}
            >
              <i className="ti ti-user-circle" />
              <span>Account Settings</span>
            </button>

            <button
              type="button"
              className={`sidebar-link ${activeTab === "password" ? "active" : ""}`}
              onClick={() => switchTab("password")}
            >
              <i className="ti ti-lock" />
              <span>Password & Security</span>
            </button>
          </div>

          {canManagePage && (
            <div className="sidebar-nav-group" style={{ marginTop: "12px", paddingTop: "12px", borderTop: "1px solid var(--d-border)" }}>
              <div className="sidebar-nav-label">Operations</div>
              <button
                type="button"
                className={`sidebar-link ${activeTab === "page" ? "active" : ""}`}
                onClick={() => switchTab("page")}
              >
                <i className="ti ti-adjustments-horizontal" />
                <span>Page Settings</span>
                <span className="sidebar-admin-tag">Admin</span>
              </button>
            </div>
          )}
        </nav>

        {/* Right Main Content Area */}
        <main className="settings-content-area">
          {/* Tab 1: Account Settings */}
          {activeTab === "account" && (
            <section className="settings-card" id="account">
              <SettingsHeader
                icon="ti ti-user-circle"
                title="Account Settings"
                description="Personalize your profile and notification delivery."
              />
              <div className="settings-card-body">
                <div className="settings-field">
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "4px" }}>
                    <label htmlFor="settings-display-name">Display name</label>
                    <div style={{ display: "flex", gap: "8px" }}>
                      {displayName !== initialDisplayName && (
                        <button
                          type="button"
                          className="settings-field-action-btn"
                          onClick={handleRevertDisplayName}
                          title="Undo changes and revert to your saved display name"
                        >
                          <i className="ti ti-arrow-back-up" /> Undo
                        </button>
                      )}
                      {user.name && displayName !== user.name && (
                        <button
                          type="button"
                          className="settings-field-action-btn"
                          onClick={handleResetToOfficialName}
                          title={`Reset to your registered account name (${user.name})`}
                        >
                          <i className="ti ti-rotate-clockwise" /> Reset to {user.name}
                        </button>
                      )}
                    </div>
                  </div>

                  <div className="settings-input-wrapper">
                    <input
                      id="settings-display-name"
                      className={`settings-input ${nameError ? "is-invalid" : displayName !== initialDisplayName ? "is-modified" : ""}`}
                      value={displayName}
                      maxLength={60}
                      placeholder="Enter your display name"
                      onChange={(e) => setDisplayName(e.target.value)}
                      onBlur={() => setDisplayName((prev) => prev.trim().replace(/\s+/g, " "))}
                    />
                    <span className="settings-char-count">{displayName.length}/60</span>
                  </div>

                  {nameError ? (
                    <span className="settings-field-hint is-error">
                      <i className="ti ti-alert-circle" /> {nameError}
                    </span>
                  ) : displayName.trim() !== initialDisplayName.trim() ? (
                    <span className="settings-field-hint is-success">
                      <i className="ti ti-check" /> Unsaved changes. Click save below to apply.
                    </span>
                  ) : (
                    <span className="settings-field-hint">
                      This name appears across your DASIGConnect workspace. Letters, numbers, spaces, and - . ' are supported.
                    </span>
                  )}
                </div>

                <div className="settings-field" style={{ marginTop: "20px" }}>
                  <span className="settings-label">Notification preferences</span>
                  <div className="settings-toggle-list">
                    <Toggle
                      icon="ti ti-bell"
                      title="In-app notifications"
                      description="Receive workflow updates inside DASIGConnect."
                      checked={notifyInApp}
                      onChange={setNotifyInApp}
                    />
                    <Toggle
                      icon="ti ti-mail"
                      title="Email notifications"
                      description="Receive important activity and account notices by email."
                      checked={notifyEmail}
                      onChange={setNotifyEmail}
                    />
                  </div>
                </div>
              </div>
              <SettingsFooter
                label="Save Account Settings"
                icon="ti ti-device-floppy"
                busy={saving === "account"}
                disabled={!isNameValid || !isAccountChanged}
                onClick={() => void saveAccount()}
              />
            </section>
          )}

          {/* Tab 2: Password & Security */}
          {activeTab === "password" && (
            <section className="settings-card" id="password">
              <SettingsHeader
                icon="ti ti-lock"
                title="Password & Security"
                description="Use a strong password to protect your account."
              />
              <div className="settings-card-body settings-password-grid">
                <div className="settings-field">
                  <label htmlFor="settings-current-password">Current password</label>
                  <div className="settings-input-wrapper">
                    <input
                      id="settings-current-password"
                      className="settings-input"
                      type={showCurrentPassword ? "text" : "password"}
                      autoComplete="current-password"
                      value={currentPassword}
                      placeholder="Enter current password"
                      onChange={(e) => setCurrentPassword(e.target.value)}
                    />
                    <button
                      type="button"
                      className="settings-eye-btn"
                      onClick={() => setShowCurrentPassword((prev) => !prev)}
                      aria-label={showCurrentPassword ? "Hide current password" : "Show current password"}
                    >
                      <i className={showCurrentPassword ? "ti ti-eye-off" : "ti ti-eye"} />
                    </button>
                  </div>
                </div>
                <div className="settings-field">
                  <label htmlFor="settings-new-password">New password</label>
                  <div className="settings-input-wrapper">
                    <input
                      id="settings-new-password"
                      className="settings-input"
                      type={showNewPassword ? "text" : "password"}
                      autoComplete="new-password"
                      value={newPassword}
                      placeholder="At least 12 characters"
                      onChange={(e) => setNewPassword(e.target.value)}
                    />
                    <button
                      type="button"
                      className="settings-eye-btn"
                      onClick={() => setShowNewPassword((prev) => !prev)}
                      aria-label={showNewPassword ? "Hide new password" : "Show new password"}
                    >
                      <i className={showNewPassword ? "ti ti-eye-off" : "ti ti-eye"} />
                    </button>
                  </div>
                  <div className="pw-rules">
                    <div className={`pw-rule${newPasswordRules.length ? " pass" : ""}`}>
                      <i className={newPasswordRules.length ? "ti ti-circle-check" : "ti ti-circle"} /> 12+ characters
                    </div>
                    <div className={`pw-rule${newPasswordRules.upper ? " pass" : ""}`}>
                      <i className={newPasswordRules.upper ? "ti ti-circle-check" : "ti ti-circle"} /> Uppercase letter
                    </div>
                    <div className={`pw-rule${newPasswordRules.lower ? " pass" : ""}`}>
                      <i className={newPasswordRules.lower ? "ti ti-circle-check" : "ti ti-circle"} /> Lowercase letter
                    </div>
                    <div className={`pw-rule${newPasswordRules.number ? " pass" : ""}`}>
                      <i className={newPasswordRules.number ? "ti ti-circle-check" : "ti ti-circle"} /> Number
                    </div>
                    <div className={`pw-rule${newPasswordRules.symbol ? " pass" : ""}`}>
                      <i className={newPasswordRules.symbol ? "ti ti-circle-check" : "ti ti-circle"} /> Special character
                    </div>
                    <div className={`pw-rule${newPasswordRules.noSpaces ? " pass" : ""}`}>
                      <i className={newPasswordRules.noSpaces ? "ti ti-circle-check" : "ti ti-circle"} /> No spaces
                    </div>
                    <div className={`pw-rule${newPasswordRules.notCommon ? " pass" : ""}`}>
                      <i className={newPasswordRules.notCommon ? "ti ti-circle-check" : "ti ti-circle"} /> Not common or sequential
                    </div>
                    <div className={`pw-rule${newPasswordRules.noIdentity ? " pass" : ""}`}>
                      <i className={newPasswordRules.noIdentity ? "ti ti-circle-check" : "ti ti-circle"} /> Does not include your name or email
                    </div>
                  </div>
                  <span className="settings-field-hint">Changing it here keeps your other signed-in sessions active.</span>
                </div>
              </div>
              <SettingsFooter
                label="Change Password"
                icon="ti ti-key"
                busy={saving === "password"}
                disabled={!currentPassword || !newPasswordOk}
                onClick={() => void savePassword()}
              />
            </section>
          )}

          {/* Tab 3: Page Settings (Admins only) */}
          {activeTab === "page" && canManagePage && (
            <div className="settings-page-overview-grid">
              {/* Card 1: Automated Watermark Studio */}
              <section className="settings-card" id="watermark-card">
                <SettingsHeader
                  icon="ti ti-droplet"
                  title="Automated Watermarking"
                  description="Brand overlay automatically applied to approved photo posts."
                />
                <div className="settings-card-body">
                  <Toggle
                    title="Enable automated watermarking"
                    description="Apply watermarks automatically upon submission approval."
                    checked={watermarkEnabled}
                    onChange={setWatermarkEnabled}
                  />

                  <div className="settings-studio-summary-box">
                    <div className="settings-summary-stat">
                      <span className="settings-summary-stat-label">Configuration Scope</span>
                      <span className="settings-summary-stat-val">
                        <span className="wm-elements-badge">
                          <i className="ti ti-world" /> Global Default
                        </span>
                      </span>
                    </div>
                    <div className="settings-summary-stat">
                      <span className="settings-summary-stat-label">Status</span>
                      <span className={`settings-summary-stat-badge ${watermarkEnabled ? "is-active" : "is-inactive"}`}>
                        {watermarkEnabled ? "Active" : "Disabled"}
                      </span>
                    </div>
                  </div>
                </div>
                <footer className="settings-card-footer">
                  <button
                    type="button"
                    className="settings-save-button"
                    onClick={openStudio}
                  >
                    <i className="ti ti-palette" />
                    Open Watermark Studio
                  </button>
                </footer>
              </section>

              {/* Card 2: Facebook Integration */}
              <section className="settings-card" id="facebook-card">
                <SettingsHeader
                  icon="ti ti-brand-facebook"
                  title="Facebook Integration"
                  description="Identify the target Facebook Page used for automated publishing."
                />
                <div className="settings-card-body">
                  <div className="settings-field">
                    <label htmlFor="settings-facebook-id">Facebook Page ID</label>
                    <div className="settings-input-with-icon">
                      <i className="ti ti-brand-facebook" />
                      <input
                        id="settings-facebook-id"
                        className="settings-input"
                        value={facebookPageId}
                        maxLength={255}
                        placeholder="Enter Facebook Page ID"
                        onChange={(e) => setFacebookPageId(e.target.value)}
                      />
                    </div>
                    <span className="settings-field-hint">Access tokens and credentials remain secured separately.</span>
                  </div>
                </div>
                <SettingsFooter
                  label="Save Facebook ID"
                  icon="ti ti-device-floppy"
                  busy={saving === "page"}
                  onClick={() => void savePage()}
                />
              </section>

              {/* Card 3: Facebook Messenger Alerts */}
              <section className="settings-card" id="messenger-card">
                <SettingsHeader
                  icon="ti ti-brand-messenger"
                  title="Facebook Messenger Alerts"
                  description="Receive instant alerts for submissions, schedule warnings, and critical publishing events directly on Messenger."
                  accent={
                    messengerStatus?.connected ? (
                      <span className="settings-admin-chip" style={{ background: "#dcfce7", color: "#166534" }}>
                        <i className="ti ti-circle-check" /> Connected
                      </span>
                    ) : (
                      <span className="settings-admin-chip">
                        <i className="ti ti-plug" /> Integration
                      </span>
                    )
                  }
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
                        <p>Link your personal Facebook Messenger to receive automated real-time alerts (T-01, T-07, T-11, T-12) directly from DASIGConnect.</p>
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
                            <i className={saving === "messenger" ? "ti ti-loader-2 settings-spinner" : "ti ti-key"} />
                            Generate Link Code
                          </button>
                        </div>
                      )}
                    </div>
                  )}
                </div>
              </section>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}

function SettingsHeader({ icon, title, description, accent }: { icon: string; title: string; description: string; accent?: React.ReactNode }) {
  return (
    <header className="settings-card-header">
      <span className="settings-card-icon"><i className={icon} /></span>
      <div className="settings-card-heading">
        <h2>{title}</h2>
        <p>{description}</p>
      </div>
      {accent}
    </header>
  );
}

function SettingsFooter({ label, icon, busy, disabled, onClick }: { label: string; icon: string; busy: boolean; disabled?: boolean; onClick: () => void }) {
  return (
    <footer className="settings-card-footer">
      <button type="button" className="settings-save-button" disabled={busy || disabled} aria-busy={busy} onClick={onClick}>
        <i className={busy ? "ti ti-loader-2 settings-spinner" : icon} />
        {busy ? "Saving…" : label}
      </button>
    </footer>
  );
}

function Toggle({ icon, title, description, checked, onChange }: { icon?: string; title: string; description: string; checked: boolean; onChange: (checked: boolean) => void }) {
  return (
    <label className="settings-toggle-row">
      {icon && <span className="settings-toggle-icon"><i className={icon} /></span>}
      <span className="settings-toggle-copy">
        <strong>{title}</strong>
        <span>{description}</span>
      </span>
      <input className="settings-toggle-input" type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      <span className="settings-switch"><span /></span>
    </label>
  );
}

function formatRole(role: User["role"]) {
  return role === "admin" ? "Admin" : role === "moderator" ? "Moderator" : "Contributor";
}
