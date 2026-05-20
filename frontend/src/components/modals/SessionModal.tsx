interface SessionModalProps {
  open: boolean
  email: string
  password: string
  error: boolean
  onEmailChange: (value: string) => void
  onPasswordChange: (value: string) => void
  onTogglePassword: () => void
  onSubmit: () => void
  showPassword: boolean
}

export default function SessionModal({
  open,
  email,
  password,
  error,
  onEmailChange,
  onPasswordChange,
  onTogglePassword,
  onSubmit,
  showPassword,
}: SessionModalProps) {
  return (
    <div id="session-modal" className={open ? '' : 'hidden'}>
      <div className="modal-card">
        <div className="modal-icon">
          <i className="ti ti-clock-x"></i>
        </div>
        <div className="modal-title">Session expired.</div>
        <div className="modal-desc">
          Your session timed out due to 8 hours of inactivity. Sign in again to
          resume — you won't lose your current page.
        </div>
        <div
          id="modal-err"
          className={`alert alert-err${error ? '' : ' hidden'}`}
          style={{ marginBottom: 12 }}
        >
          <i className="ti ti-alert-circle"></i> Invalid credentials. Please try
          again.
        </div>
        <div className="fgroup">
          <label className="flabel">Institutional Email</label>
          <input
            id="modal-email"
            className="finput"
            type="email"
            placeholder="yourname@institution.edu.ph"
            value={email}
            onChange={(event) => onEmailChange(event.target.value)}
          />
        </div>
        <div className="fgroup">
          <label className="flabel">Password</label>
          <div className="pw-wrap">
            <input
              id="modal-pw"
              className="finput"
              type={showPassword ? 'text' : 'password'}
              placeholder="••••••••••"
              value={password}
              onChange={(event) => onPasswordChange(event.target.value)}
            />
            <button
              type="button"
              className="eye-btn"
              onClick={onTogglePassword}
              aria-label="Toggle"
            >
              <i className={showPassword ? 'ti ti-eye' : 'ti ti-eye-off'}></i>
            </button>
          </div>
        </div>
        <button type="button" className="btn-primary" onClick={onSubmit}>
          <i className="ti ti-login"></i> Sign In & Resume
        </button>
      </div>
    </div>
  )
}
