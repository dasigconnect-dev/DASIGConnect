import "../../styles/dasig-loader.css";
import "../../styles/settings.css";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useLocation, useNavigate } from "react-router-dom";
import type { User } from "../../types/auth.types";
import type { WatermarkElement } from "../../types/watermark.types";
import {
  changePassword,
  getMe,
  getPageSettings,
  requestPasswordReset,
  updateAccountSettings,
  updatePageSettings,
  type PageSettingsResponse,
  type UserProfileResponse,
} from "../../api/authApi";
import { createMessengerLinkCode, disconnectMessenger, getMessengerConnectionStatus, type MessengerConnection, type MessengerLinkCode } from "../../api/messengerApi";
import { getWatermarkConfiguration, saveWatermarkConfiguration } from "../../api/watermarkApi";
import WatermarkCanvasEditor from "../settings/components/WatermarkCanvasEditor";
import { useToast } from "../../context/ToastContext";
import { authenticatedQueryMeta } from "../../lib/queryClient";
import { queryKeys } from "../../lib/queryKeys";
import { firstPasswordError, getPasswordRules } from "../../lib/passwordPolicy";

interface Props {
  user: User;
  onProfileUpdated: () => Promise<void>;
}

type SettingsTab = "account" | "password" | "page";

type ProfileSettingsForm = {
  name: string;
  notifyInApp: boolean;
  notifyEmail: boolean;
};

const PROFILE_CACHE_TTL_MS = 60_000;
const PAGE_SETTINGS_STALE_TIME_MS = 5 * 60_000;
const WATERMARK_STALE_TIME_MS = 5 * 60_000;

function getUserCacheScope(user: User) {
  return user.id ?? user.email.trim().toLowerCase();
}

function getProfileSettingsForm(data: UserProfileResponse): ProfileSettingsForm {
  const fullName = [data.firstName, data.lastName].filter(Boolean).join(" ");
  return {
    name: data.displayName || fullName || "",
    notifyInApp: data.notifyInApp,
    notifyEmail: data.notifyEmail,
  };
}

export default function AccountSettingsScreen({ user, onProfileUpdated }: Props) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const location = useLocation();
  const navigate = useNavigate();

  const canManagePage = user.role === "admin";
  // Messenger alerts are a per-account delivery channel; only moderators and
  // admins actually receive Messenger deliveries (see NotificationEventListener).
  const canUseMessenger = user.role === "moderator" || user.role === "admin";
  const userScope = getUserCacheScope(user);

  const activeTab = getActiveTabFromHash(location.hash, canManagePage);
  const isStudioOpen = canManagePage && location.hash.replace("#", "") === "watermark-studio";

  const seedName = user.displayName || user.name;
  const [displayName, setDisplayName] = useState(seedName);
  const [initialDisplayName, setInitialDisplayName] = useState(seedName);
  const [notifyInApp, setNotifyInApp] = useState(true);
  const [initialNotifyInApp, setInitialNotifyInApp] = useState(true);
  const [notifyEmail, setNotifyEmail] = useState(true);
  const [initialNotifyEmail, setInitialNotifyEmail] = useState(true);
  const [currentPassword, setCurrentPassword] = useState("");
  const [showCurrentPassword, setShowCurrentPassword] = useState(false);
  const [currentPwEditable, setCurrentPwEditable] = useState(false);
  const [resetLinkSending, setResetLinkSending] = useState(false);
  const [resetLinkSent, setResetLinkSent] = useState(false);
  const [newPassword, setNewPassword] = useState("");
  const [showNewPassword, setShowNewPassword] = useState(false);
  const [facebookPageId, setFacebookPageId] = useState("");

  // Watermark Studio States
  const [watermarkEnabled, setWatermarkEnabled] = useState(true);
  const [watermarkElements, setWatermarkElements] = useState<WatermarkElement[]>([]);

  // Messenger Integration States
  const [messengerStatus, setMessengerStatus] = useState<MessengerConnection | null>(null);
  const [linkCode, setLinkCode] = useState<MessengerLinkCode | null>(null);
  const [copiedCode, setCopiedCode] = useState(false);
  const [messengerExpanded, setMessengerExpanded] = useState(false);

  const [saving, setSaving] = useState<"account" | "password" | "page" | "watermark" | "messenger" | null>(null);
  const pageInstitutionId = null;
  const profileQueryKey = queryKeys.settings.profile({ userId: userScope });
  const pageSettingsQueryKey = queryKeys.settings.page({
    role: user.role,
    userId: userScope,
    institutionId: pageInstitutionId,
  });
  const watermarkQueryKey = queryKeys.settings.watermark({
    role: user.role,
    userId: userScope,
    institutionId: pageInstitutionId,
  });
  const profileHydratedRef = useRef(false);
  const pageSettingsHydratedRef = useRef(false);
  const watermarkHydratedRef = useRef(false);
  const pageSettingsErrorNotifiedRef = useRef(false);
  const watermarkErrorNotifiedRef = useRef(false);
  const newPasswordRules = getPasswordRules(newPassword, [
    user.email,
    user.name,
    user.displayName,
  ]);
  const newPasswordOk = Object.values(newPasswordRules).every(Boolean);

  const profileQuery = useQuery({
    queryKey: profileQueryKey,
    queryFn: ({ signal }) => getMe(signal),
    staleTime: PROFILE_CACHE_TTL_MS,
    meta: authenticatedQueryMeta,
  });

  const pageSettingsQuery = useQuery({
    queryKey: pageSettingsQueryKey,
    queryFn: ({ signal }) => getPageSettings(pageInstitutionId, signal),
    enabled: canManagePage && activeTab === "page",
    staleTime: PAGE_SETTINGS_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const watermarkQuery = useQuery({
    queryKey: watermarkQueryKey,
    queryFn: ({ signal }) => getWatermarkConfiguration(pageInstitutionId, signal),
    enabled: canManagePage && activeTab === "page",
    staleTime: WATERMARK_STALE_TIME_MS,
    meta: authenticatedQueryMeta,
  });

  const initialLoading = profileQuery.isLoading;
  const watermarkLoading = watermarkQuery.isLoading;

  async function invalidateAccountSettingsDependencies() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["settings"] }),
      queryClient.invalidateQueries({ queryKey: ["users"] }),
      queryClient.invalidateQueries({ queryKey: ["notifications"] }),
    ]);
  }

  async function invalidatePageSettingsDependencies() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["settings"] }),
      queryClient.invalidateQueries({ queryKey: ["submissions"] }),
      queryClient.invalidateQueries({ queryKey: ["calendar-events"] }),
    ]);
  }

  async function invalidateWatermarkSettingsDependencies() {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["settings"] }),
      queryClient.invalidateQueries({ queryKey: ["submissions"] }),
      queryClient.invalidateQueries({ queryKey: ["validation"] }),
    ]);
  }

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

  const loadMessenger = () => {
    if (!canUseMessenger) return;
    getMessengerConnectionStatus()
      .then((data) => setMessengerStatus(data))
      .catch(() => setMessengerStatus(null));
  };

  function switchTab(tab: SettingsTab) {
    if (tab === "page" && !canManagePage) return;
    navigate(`/settings#${tab}`, { replace: true });
  }

  function openStudio() {
    if (!canManagePage) return;
    navigate(`/settings#watermark-studio`, { replace: true });
  }

  function closeStudio() {
    navigate(`/settings#${canManagePage ? "page" : "account"}`, { replace: true });
  }

  // Hydrate editable forms once from query data so background refetches do not
  // replace in-progress edits.
  useEffect(() => {
    let isCurrent = true;
    if (profileQuery.data && !profileHydratedRef.current) {
      const profileForm = getProfileSettingsForm(profileQuery.data.data);
      setDisplayName(profileForm.name);
      setInitialDisplayName(profileForm.name);
      setNotifyInApp(profileForm.notifyInApp);
      setInitialNotifyInApp(profileForm.notifyInApp);
      setNotifyEmail(profileForm.notifyEmail);
      setInitialNotifyEmail(profileForm.notifyEmail);
      profileHydratedRef.current = true;
    }

    if (canUseMessenger) {
      getMessengerConnectionStatus()
        .then((data) => {
          if (isCurrent) setMessengerStatus(data);
        })
        .catch(() => {
          if (isCurrent) setMessengerStatus(null);
        });
    }

    return () => {
      isCurrent = false;
    };
  }, [canUseMessenger, profileQuery.data]);

  // Page and watermark data load lazily through queries when admins open the
  // Page tab.
  useEffect(() => {
    if (pageSettingsQuery.data && !pageSettingsHydratedRef.current) {
      setFacebookPageId(pageSettingsQuery.data.data.facebookPageId || "");
      pageSettingsHydratedRef.current = true;
    }

    if (watermarkQuery.data && !watermarkHydratedRef.current) {
      setWatermarkEnabled(watermarkQuery.data.data.enabled);
      setWatermarkElements(watermarkQuery.data.data.elements || []);
      watermarkHydratedRef.current = true;
    }

    if (pageSettingsQuery.isError && !pageSettingsErrorNotifiedRef.current) {
      pageSettingsErrorNotifiedRef.current = true;
      toast.error("Unable to load Page Settings.");
    }

    if (watermarkQuery.isError && !watermarkErrorNotifiedRef.current) {
      watermarkErrorNotifiedRef.current = true;
      toast.error("Unable to load Watermark configuration.");
    }
  }, [pageSettingsQuery.data, pageSettingsQuery.isError, toast, watermarkQuery.data, watermarkQuery.isError]);

  async function saveAccount() {
    const cleanName = displayName.trim().replace(/\s+/g, " ");
    const err = validateDisplayName(cleanName);
    if (err) return toast.error(err);

    setSaving("account");
    try {
      const { data } = await updateAccountSettings({ displayName: cleanName, notifyInApp, notifyEmail });
      const profileForm = getProfileSettingsForm(data);
      setDisplayName(profileForm.name);
      setInitialDisplayName(profileForm.name);
      setNotifyInApp(profileForm.notifyInApp);
      setInitialNotifyInApp(profileForm.notifyInApp);
      setNotifyEmail(profileForm.notifyEmail);
      setInitialNotifyEmail(profileForm.notifyEmail);
      queryClient.setQueryData(profileQueryKey, { data });
      profileHydratedRef.current = true;
      await invalidateAccountSettingsDependencies();
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

  async function handleSendResetLink() {
    if (resetLinkSending) return;
    setResetLinkSending(true);
    try {
      await requestPasswordReset(user.email);
      setResetLinkSent(true);
      toast.success(`We've emailed a password reset link to ${user.email}.`);
    } catch {
      toast.error("Couldn't send the reset link. Please try again.");
    } finally {
      setResetLinkSending(false);
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
      setCurrentPwEditable(false);
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
      const { data } = await updatePageSettings({ facebookPageId }, pageInstitutionId);
      setFacebookPageId(data.facebookPageId || "");
      queryClient.setQueryData(pageSettingsQueryKey, { data } satisfies { data: PageSettingsResponse });
      pageSettingsHydratedRef.current = true;
      pageSettingsErrorNotifiedRef.current = false;
      await invalidatePageSettingsDependencies();
      toast.success("Facebook Page ID updated.");
    } catch {
      toast.error("Unable to update Facebook Page ID.");
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
      setWatermarkEnabled(data.enabled);
      setWatermarkElements(data.elements || []);
      queryClient.setQueryData(watermarkQueryKey, { data });
      watermarkHydratedRef.current = true;
      watermarkErrorNotifiedRef.current = false;
      await invalidateWatermarkSettingsDependencies();
      toast.success("Watermark settings saved.");
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
      toast.success("Link code ready — copy the command and send it in Messenger.");
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
      setMessengerExpanded(false);
      toast.success("Facebook Messenger disconnected.");
    } catch {
      toast.error("Unable to disconnect Messenger.");
    } finally {
      setSaving(null);
    }
  }

  function handleCopyCode() {
    if (!linkCode) return;
    // linkCode.code is already the full command ("CONNECT <token>") from the API.
    void navigator.clipboard.writeText(linkCode.code).then(() => {
      setCopiedCode(true);
      toast.success("Command copied — paste it into Messenger.");
      setTimeout(() => setCopiedCode(false), 3000);
    });
  }

  // Live countdown for the link code (server issues a 10-minute window). A
  // ticking clock forces the re-render; the value itself is derived in render.
  const [nowMs, setNowMs] = useState(() => Date.now());
  useEffect(() => {
    if (!linkCode) return;
    const id = window.setInterval(() => setNowMs(Date.now()), 1000);
    return () => window.clearInterval(id);
  }, [linkCode]);
  const codeSecondsLeft = linkCode
    ? Math.max(0, Math.round((new Date(linkCode.expiresAt).getTime() - nowMs) / 1000))
    : 0;

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
                aria-busy={saving === "watermark"}
                onClick={() => void saveWatermark()}
              >
                <i className={saving === "watermark" ? "ti ti-loader-2 settings-spinner" : "ti ti-device-floppy"} />
                {saving === "watermark" ? "Saving..." : "Save"}
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
        <div className="settings-content-area">
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

                    {canUseMessenger && (
                      <>
                        <div className="settings-toggle-row settings-channel-row">
                          <span className="settings-toggle-icon"><i className="ti ti-brand-messenger" /></span>
                          <span className="settings-toggle-copy">
                            <strong>Facebook Messenger</strong>
                            <span>
                              {messengerStatus?.connected
                                ? `Connected${messengerStatus.linkedAt ? " " + new Date(messengerStatus.linkedAt).toLocaleDateString() : ""} — alerts also push to Messenger.`
                                : "Also push your alerts to your personal Facebook Messenger."}
                            </span>
                          </span>
                          {messengerStatus?.connected ? (
                            <button
                              type="button"
                              className="settings-channel-btn is-danger"
                              disabled={saving === "messenger"}
                              onClick={() => void handleDisconnectMessenger()}
                            >
                              Disconnect
                            </button>
                          ) : (
                            <button
                              type="button"
                              className="settings-channel-btn"
                              aria-expanded={messengerExpanded}
                              onClick={() => setMessengerExpanded((v) => !v)}
                            >
                              {messengerExpanded ? "Cancel" : "Set up"}
                            </button>
                          )}
                        </div>

                        {messengerExpanded && !messengerStatus?.connected && (
                          <div className="settings-channel-panel">
                            <div className="settings-messenger-steps">
                              <ol>
                                <li>Generate a one-time code below — it&apos;s valid for 10 minutes.</li>
                                <li>
                                  In Messenger, open a chat with the official <strong>DASIGConnect</strong>{" "}
                                  Page and send it the command shown below (use <strong>Copy</strong> so it&apos;s exact).
                                </li>
                                <li>
                                  The Page replies to confirm; then hit{" "}
                                  <strong>I&apos;ve sent it — check status</strong>.
                                </li>
                              </ol>
                            </div>

                            {linkCode ? (
                              <div className="settings-messenger-linkcode">
                                <span className="settings-messenger-code-label">
                                  Send this exact message to the DASIGConnect Page
                                </span>
                                <div className="settings-messenger-code-container">
                                  <code className="settings-messenger-code-value">{linkCode.code}</code>
                                  <button
                                    type="button"
                                    className="settings-messenger-copy-btn"
                                    onClick={handleCopyCode}
                                  >
                                    <i className={copiedCode ? "ti ti-check" : "ti ti-copy"} />
                                    {copiedCode ? "Copied" : "Copy"}
                                  </button>
                                </div>
                                <div className="settings-messenger-code-meta">
                                  <span className={codeSecondsLeft <= 60 ? "is-expiring" : undefined}>
                                    <i className="ti ti-clock" />{" "}
                                    {codeSecondsLeft > 0
                                      ? `Expires in ${Math.floor(codeSecondsLeft / 60)}:${String(codeSecondsLeft % 60).padStart(2, "0")}`
                                      : "Code expired"}
                                  </span>
                                  <span className="settings-messenger-meta-actions">
                                    {codeSecondsLeft === 0 && (
                                      <button
                                        type="button"
                                        className="settings-messenger-link-btn"
                                        disabled={saving === "messenger"}
                                        onClick={() => void generateMessengerCode()}
                                      >
                                        Generate new code
                                      </button>
                                    )}
                                    <button
                                      type="button"
                                      className="settings-messenger-link-btn"
                                      onClick={loadMessenger}
                                    >
                                      I&apos;ve sent it — check status
                                    </button>
                                  </span>
                                </div>
                              </div>
                            ) : (
                              <button
                                type="button"
                                className="settings-save-button settings-messenger-generate-btn"
                                disabled={saving === "messenger"}
                                onClick={() => void generateMessengerCode()}
                              >
                                <i className={saving === "messenger" ? "ti ti-loader-2 settings-spinner" : "ti ti-key"} />
                                {saving === "messenger" ? "Generating…" : "Generate link code"}
                              </button>
                            )}
                          </div>
                        )}
                      </>
                    )}
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
              <form
                onSubmit={(event: FormEvent<HTMLFormElement>) => {
                  event.preventDefault();
                  void savePassword();
                }}
              >
              <div className="settings-card-body settings-password-grid">
                <div className="settings-field">
                  <label htmlFor="settings-current-password">Current password</label>
                  <div className="settings-input-wrapper">
                    <input
                      id="settings-current-password"
                      name="current-password"
                      className="settings-input"
                      type={showCurrentPassword ? "text" : "password"}
                      autoComplete="current-password"
                      // Read-only until focused so the browser doesn't autofill
                      // the saved password on load — the user must type it.
                      readOnly={!currentPwEditable}
                      onFocus={() => setCurrentPwEditable(true)}
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
                  {resetLinkSent ? (
                    <span className="settings-field-hint is-success settings-forgot-pw-btn">
                      <i className="ti ti-mail-check" /> Reset link sent to {user.email}
                    </span>
                  ) : (
                    <button
                      type="button"
                      className="settings-field-action-btn settings-forgot-pw-btn"
                      disabled={resetLinkSending}
                      onClick={() => void handleSendResetLink()}
                    >
                      <i className={resetLinkSending ? "ti ti-loader-2 settings-spinner" : "ti ti-mail"} />
                      {resetLinkSending ? "Sending…" : "Forgot your password?"}
                    </button>
                  )}
                </div>
                <div className="settings-field">
                  <label htmlFor="settings-new-password">New password</label>
                  <div className="settings-input-wrapper">
                    <input
                      id="settings-new-password"
                      name="new-password"
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
              </form>
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
                <footer className="settings-card-footer" style={{ gap: 10 }}>
                  <button
                    type="button"
                    className="settings-launch-studio-btn"
                    onClick={openStudio}
                  >
                    <i className="ti ti-palette" />
                    Open Watermark Studio
                  </button>
                  <button
                    type="button"
                    className="settings-save-button"
                    disabled={saving === "watermark" || watermarkLoading}
                    aria-busy={saving === "watermark"}
                    onClick={() => void saveWatermark()}
                  >
                    <i className={saving === "watermark" ? "ti ti-loader-2 settings-spinner" : "ti ti-device-floppy"} />
                    {saving === "watermark" ? "Saving…" : "Save"}
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
            </div>
          )}
        </div>
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

function getActiveTabFromHash(hashValue: string, canManagePage: boolean): SettingsTab {
  const hash = hashValue.replace("#", "");
  if (hash === "password") return "password";
  if ((hash === "page" || hash === "watermark-studio") && canManagePage) return "page";
  return "account";
}
