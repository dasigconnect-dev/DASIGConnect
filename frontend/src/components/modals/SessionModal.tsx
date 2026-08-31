import type { FormEvent } from "react"
import Spinner from "../common/Spinner"

interface SessionModalProps {
  open: boolean
  email: string
  password: string
  error: string | boolean | null
  submitLoading: boolean
  signOutLoading: boolean
  onEmailChange: (value: string) => void
  onPasswordChange: (value: string) => void
  onTogglePassword: () => void
  onSubmit: () => void
  onSignOut: () => void
  showPassword: boolean
}

export default function SessionModal({
  open,
  email,
  password,
  error,
  submitLoading,
  signOutLoading,
  onEmailChange,
  onPasswordChange,
  onTogglePassword,
  onSubmit,
  onSignOut,
  showPassword,
}: SessionModalProps) {
  const actionLoading = submitLoading || signOutLoading

  return (
    <div id="session-modal" className={open ? "" : "hidden"}>
      <div className="modal-card">
        <div className="modal-icon">
          <i className="ti ti-clock-x"></i>
        </div>
        <div className="modal-title">Session expired.</div>
        <div className="modal-desc">
          Your session timed out due to 8 hours of inactivity. Sign in again to
          resume - you won't lose your current page.
        </div>
        <div
          id="modal-err"
          className={`alert alert-err${error ? "" : " hidden"}`}
          style={{ marginBottom: 12 }}
        >
          <i className="ti ti-alert-circle"></i>{" "}
          {typeof error === "string" && error
            ? error
            : "Invalid credentials. Please try again."}
        </div>
        <form
          onSubmit={(event: FormEvent<HTMLFormElement>) => {
            event.preventDefault()
            onSubmit()
          }}
        >
          <div className="fgroup">
            <label className="flabel" htmlFor="modal-email">Institutional Email</label>
            <input
              id="modal-email"
              name="email"
              className="finput"
              type="email"
              autoComplete="email"
              placeholder="yourname@institution.edu.ph"
              disabled={actionLoading}
              value={email}
              onChange={(event) => onEmailChange(event.target.value)}
            />
          </div>
          <div className="fgroup">
            <label className="flabel" htmlFor="modal-pw">Password</label>
            <div className="pw-wrap">
              <input
                id="modal-pw"
                name="password"
                className="finput"
                type={showPassword ? "text" : "password"}
                placeholder="**********"
                autoComplete="current-password"
                disabled={actionLoading}
                value={password}
                onChange={(event) => onPasswordChange(event.target.value)}
              />
              <button
                type="button"
                className="eye-btn"
                onClick={onTogglePassword}
                disabled={actionLoading}
                aria-label="Toggle password visibility"
              >
                <i className={showPassword ? "ti ti-eye" : "ti ti-eye-off"}></i>
              </button>
            </div>
          </div>
          <button
            type="submit"
            className="btn-primary"
            disabled={actionLoading}
            aria-busy={submitLoading}
          >
            {submitLoading ? (
              <Spinner size="xs" color="white" aria-label="Signing in" />
            ) : (
              <i className="ti ti-login"></i>
            )}
            Sign In & Resume
          </button>
        </form>
        <button
          type="button"
          className="btn-ghost session-signout-btn"
          onClick={onSignOut}
          disabled={actionLoading}
          aria-busy={signOutLoading}
        >
          {signOutLoading ? (
            <Spinner size="xs" color="inherit" aria-label="Signing out" />
          ) : (
            <i className="ti ti-logout"></i>
          )}
          Sign Out
        </button>
      </div>
    </div>
  )
}
