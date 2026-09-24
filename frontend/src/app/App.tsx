import { lazy, Suspense, useEffect } from "react";
import { Routes, Route, useNavigate, Navigate } from "react-router-dom";
import DashboardLayout from "../components/layout/DashboardLayout";
import AdminPromotionBanner from "../components/layout/AdminPromotionBanner";
import SessionModal from "../components/modals/SessionModal";
import Toast from "../components/common/Toast";
import AppErrorBoundary from "../components/common/AppErrorBoundary";
import NotFoundPage from "../components/common/NotFoundPage";
import LoginSplash from "../components/common/LoginSplash";
import PageLoader from "../components/common/PageLoader";
import { RequireAdmin, RequireReviewer, RequireSignedIn } from "../components/common/RouteGates";
import { useAuthSession } from "../features/auth/hooks/useAuthSession";

const TABLER_ICONS_STYLESHEET = "https://cdn.jsdelivr.net/npm/@tabler/icons-webfont@2.44.0/tabler-icons.min.css";

const LandingPage = lazy(() => import("../features/landing/LandingPage"));
const LoginScreen = lazy(() => import("../features/auth/LoginScreen"));
const ForgotScreen = lazy(() => import("../features/auth/ForgotScreen"));
const ForgotSentScreen = lazy(() => import("../features/auth/ForgotSentScreen"));
const ResetPasswordScreen = lazy(() => import("../features/auth/ResetPasswordScreen"));
const InviteScreen = lazy(() => import("../features/auth/InviteScreen"));
const NoAccountScreen = lazy(() => import("../features/auth/NoAccountScreen"));
const AccountSettingsScreen = lazy(() => import("../features/auth/AccountSettingsScreen"));
const DashboardScreen = lazy(() => import("../features/dashboard/DashboardScreen"));
const RecentActivityScreen = lazy(() => import("../features/dashboard/RecentActivityScreen"));
const SubmissionScreen = lazy(() => import("../features/submission/SubmissionScreen"));
const SubmissionListScreen = lazy(() => import("../features/submission/SubmissionListScreen"));
const ValidationQueueScreen = lazy(() => import("../features/validation/ValidationQueueScreen"));
const InstitutionManagementScreen = lazy(() => import("../features/institution-management/InstitutionManagementScreen"));
const AdminManagementScreen = lazy(() => import("../features/administrator-management/AdministratorManagementScreen"));
const UserManagementScreen = lazy(() => import("../features/user-management/UserManagementScreen"));
const SystemHealthScreen = lazy(() => import("../features/system-health/SystemHealthScreen"));
const AuditLogScreen = lazy(() => import("../features/audit-log/AuditLogScreen"));
const CalendarScreen = lazy(() => import("../features/calendar/CalendarScreen"));
const MediaRepositoryScreen = lazy(() => import("../features/media-repository/MediaRepositoryScreen"));
const NotificationsScreen = lazy(() => import("../features/notifications/NotificationsScreen"));
const AnalyticsDashboardPage = lazy(() => import("../features/analytics/AnalyticsDashboardPage"));

/** Routes. Session state and every auth flow live in useAuthSession. */
function App() {
  const navigate = useNavigate();
  const auth = useAuthSession();
  const { currentUser, passwordReset, invite, layout, sessionModal } = auth;

  useEffect(() => {
    if (document.querySelector<HTMLLinkElement>('link[data-dasig-tabler-icons="true"]')) return;
    const link = document.createElement("link");
    link.rel = "stylesheet";
    link.href = TABLER_ICONS_STYLESHEET;
    link.crossOrigin = "anonymous";
    link.dataset.dasigTablerIcons = "true";
    document.head.appendChild(link);
  }, []);

  if (!auth.authReady) {
    return <PageLoader />;
  }

  const backToLogin = () => navigate("/login");
  const resetPasswordScreen = (
    <ResetPasswordScreen
      active={true}
      password={passwordReset.reset.password}
      confirmPassword={passwordReset.reset.confirmPassword}
      showPassword={passwordReset.reset.showPassword}
      showConfirmPassword={passwordReset.reset.showConfirmPassword}
      loading={passwordReset.reset.loading}
      error={passwordReset.reset.error}
      success={passwordReset.reset.success}
      onPasswordChange={passwordReset.reset.setPassword}
      onConfirmPasswordChange={passwordReset.reset.setConfirmPassword}
      onTogglePassword={passwordReset.reset.toggleShowPassword}
      onToggleConfirmPassword={passwordReset.reset.toggleShowConfirmPassword}
      onSubmit={() => void passwordReset.reset.submit()}
      onBack={backToLogin}
    />
  );

  return (
    <>
      <Toast />
      <LoginSplash user={auth.splash.user} visible={auth.splash.visible} />
      <AppErrorBoundary>
        <Suspense fallback={<PageLoader />}>
          <Routes>
            {/* ── Public ─────────────────────────────────────────────── */}
            <Route path="/" element={<LandingPage user={currentUser} />} />
            <Route
              path="/login"
              element={
                <LoginScreen
                  active={true}
                  email={auth.login.email}
                  password={auth.login.password}
                  showPassword={auth.login.showPassword}
                  loginError={auth.login.error}
                  attempts={auth.login.attempts}
                  lockRemaining={auth.login.lockRemaining}
                  onEmailChange={auth.login.setEmail}
                  onPasswordChange={auth.login.setPassword}
                  onTogglePassword={auth.login.toggleShowPassword}
                  onLogin={() => void auth.login.submit()}
                  onForgot={() => navigate("/forgot-password")}
                  onNoAccount={() => navigate("/no-account")}
                  onRequestReset={() => navigate("/forgot-password")}
                  loading={auth.login.loading}
                />
              }
            />
            <Route
              path="/forgot-password"
              element={
                <ForgotScreen
                  active={true}
                  email={passwordReset.forgot.email}
                  onEmailChange={passwordReset.forgot.setEmail}
                  onSubmit={() => void passwordReset.forgot.submit()}
                  onBack={backToLogin}
                  loading={passwordReset.forgot.loading}
                />
              }
            />
            <Route
              path="/forgot-password-sent"
              element={<ForgotSentScreen active={true} email={passwordReset.forgot.sentEmail} onBack={backToLogin} />}
            />
            <Route path="/reset-password" element={resetPasswordScreen} />
            <Route path="/forgot-password/reset" element={resetPasswordScreen} />
            <Route
              path="/invite"
              element={
                <InviteScreen
                  active={true}
                  state={invite.state}
                  email={invite.email}
                  roleLabel={invite.roleLabel}
                  institution={invite.institution}
                  firstName={invite.firstName}
                  lastName={invite.lastName}
                  password={invite.password}
                  confirmPassword={invite.confirmPassword}
                  rules={invite.rules}
                  inviteCountdown={invite.countdown}
                  onFirstNameChange={invite.setFirstName}
                  onLastNameChange={invite.setLastName}
                  onPasswordChange={invite.setPassword}
                  onConfirmPasswordChange={invite.setConfirmPassword}
                  onTogglePassword={invite.toggleShowPassword}
                  onToggleConfirmPassword={invite.toggleShowConfirmPassword}
                  onActivate={() => void invite.activate()}
                  onBackToLogin={backToLogin}
                  showPassword={invite.showPassword}
                  showConfirmPassword={invite.showConfirmPassword}
                  loading={invite.loading}
                />
              }
            />
            <Route path="/no-account" element={<NoAccountScreen active={true} onBack={backToLogin} />} />

            {/* ── Signed in: dashboard layout ────────────────────────── */}
            <Route
              element={
                currentUser ? (
                  <>
                    <AdminPromotionBanner user={currentUser} />
                    <DashboardLayout
                      user={currentUser}
                      showBanner={layout.showBanner}
                      bannerTime={layout.bannerTime}
                      showDropdown={layout.showDropdown}
                      onToggleDropdown={layout.toggleDropdown}
                      onDismissBanner={layout.dismissBanner}
                      onStayLoggedIn={layout.stayLoggedIn}
                      onLogout={() => void layout.logout()}
                      logoutLoading={layout.logoutLoading}
                    />
                  </>
                ) : (
                  <Navigate to={auth.signedOutRedirect} replace />
                )
              }
            >
              {/* Any signed-in role — this layout route already sends signed-out users to /login. */}
              <Route path="/dashboard" element={<DashboardScreen user={currentUser!} />} />
              <Route path="/dashboard/recent-activity" element={<RecentActivityScreen user={currentUser!} />} />
              <Route path="/submissions" element={<SubmissionListScreen user={currentUser!} />} />
              <Route path="/calendar" element={<CalendarScreen user={currentUser!} />} />
              <Route path="/media-repository" element={<MediaRepositoryScreen user={currentUser!} />} />
              <Route path="/notifications" element={<NotificationsScreen user={currentUser!} />} />
              <Route path="/analytics" element={<AnalyticsDashboardPage user={currentUser!} />} />
              <Route
                path="/settings"
                element={<AccountSettingsScreen user={currentUser!} onProfileUpdated={auth.refreshCurrentUserProfile} />}
              />

              {/* Moderator + Admin */}
              <Route element={<RequireReviewer user={currentUser} signedOutRedirect={auth.signedOutRedirect} />}>
                <Route path="/queue" element={<ValidationQueueScreen user={currentUser!} />} />
                <Route path="/institution-management" element={<InstitutionManagementScreen user={currentUser!} />} />
              </Route>

              {/* Admin only */}
              <Route element={<RequireAdmin user={currentUser} signedOutRedirect={auth.signedOutRedirect} />}>
                <Route
                  path="/admin/admin-management"
                  element={
                    <AdminManagementScreen user={currentUser!} onProfileUpdated={auth.refreshCurrentUserProfile} />
                  }
                />
                <Route path="/admin/user-management" element={<UserManagementScreen user={currentUser!} />} />
                <Route path="/admin/system-health" element={<SystemHealthScreen user={currentUser!} />} />
                <Route path="/admin/audit-log" element={<AuditLogScreen user={currentUser!} />} />
              </Route>
            </Route>

            {/* ── Signed in: full-screen submission editor ───────────── */}
            <Route element={<RequireSignedIn user={currentUser} signedOutRedirect={auth.signedOutRedirect} />}>
              <Route path="/submissions/new" element={<SubmissionScreen user={currentUser!} />} />
              <Route path="/submissions/:submissionId" element={<SubmissionScreen user={currentUser!} />} />
            </Route>

            <Route path="*" element={<NotFoundPage signedIn={Boolean(currentUser)} />} />
          </Routes>
        </Suspense>
      </AppErrorBoundary>

      <SessionModal
        open={sessionModal.open}
        email={sessionModal.email}
        password={sessionModal.password}
        error={sessionModal.error}
        submitLoading={sessionModal.loading}
        signOutLoading={layout.logoutLoading}
        onEmailChange={sessionModal.setEmail}
        onPasswordChange={sessionModal.setPassword}
        onTogglePassword={sessionModal.toggleShowPassword}
        onSubmit={() => void sessionModal.submit()}
        onSignOut={() => void layout.logout()}
        showPassword={sessionModal.showPassword}
      />
    </>
  );
}

export default App;
