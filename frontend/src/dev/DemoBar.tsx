interface DemoBarProps {
  onLogin: () => void
  onLoginError: () => void
  onLockout: () => void
  onForgot: () => void
  onForgotSent: () => void
  onInviteContributor: () => void
  onInviteValidator: () => void
  onInviteExpired: () => void
  onInviteAlready: () => void
  onDashboardAdmin: () => void
  onDashboardValidator: () => void
  onDashboardContributor: () => void
  onSessionWarning: () => void
  onSessionExpired: () => void
  onNoAccount: () => void
}

export default function DemoBar({
  onLogin,
  onLoginError,
  onLockout,
  onForgot,
  onForgotSent,
  onInviteContributor,
  onInviteValidator,
  onInviteExpired,
  onInviteAlready,
  onDashboardAdmin,
  onDashboardValidator,
  onDashboardContributor,
  onSessionWarning,
  onSessionExpired,
  onNoAccount,
}: DemoBarProps) {
  return (
    <div className="demo-bar">
      <span className="demo-label">Demo flow</span>
      <button className="db" onClick={onLogin}>
        1 Login
      </button>
      <button className="db" onClick={onLoginError}>
        2 Login Error
      </button>
      <button className="db" onClick={onLockout}>
        3 Lockout (5 fails)
      </button>
      <button className="db" onClick={onForgot}>
        4 Forgot Password
      </button>
      <button className="db" onClick={onForgotSent}>
        5 Reset Sent
      </button>
      <button className="db" onClick={onInviteContributor}>
        6 Invite - Contributor
      </button>
      <button className="db" onClick={onInviteValidator}>
        7 Invite - Validator
      </button>
      <button className="db" onClick={onInviteExpired}>
        8 Invite - Expired
      </button>
      <button className="db" onClick={onInviteAlready}>
        9 Invite - Already Active
      </button>
      <button className="db" onClick={onDashboardAdmin}>
        10 Dashboard - Admin
      </button>
      <button className="db" onClick={onDashboardValidator}>
        11 Dashboard - Validator
      </button>
      <button className="db" onClick={onDashboardContributor}>
        12 Dashboard - Contributor
      </button>
      <button className="db" onClick={onSessionWarning}>
        13 Session Warning
      </button>
      <button className="db" onClick={onSessionExpired}>
        14 Session Expired Modal
      </button>
      <button className="db" onClick={onNoAccount}>
        15 No Account
      </button>
    </div>
  )
}
