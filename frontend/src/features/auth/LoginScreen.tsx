import Screen from '../../components/layout/Screen'
import LeftPanel from '../../components/layout/LeftPanel'
import RightPanel from '../../components/layout/RightPanel'

interface LoginScreenProps {
  active: boolean
  email: string
  password: string
  showPassword: boolean
  loginError: string
  attempts: number
  lockRemaining: number
  loading: boolean
  onEmailChange: (value: string) => void
  onPasswordChange: (value: string) => void
  onTogglePassword: () => void
  onLogin: () => void
  onForgot: () => void
  onNoAccount: () => void
  onRequestReset: () => void
}

export default function LoginScreen({
  active,
  email,
  password,
  showPassword,
  loginError,
  attempts,
  lockRemaining,
  loading,
  onEmailChange,
  onPasswordChange,
  onTogglePassword,
  onLogin,
  onForgot,
  onNoAccount,
  onRequestReset,
}: LoginScreenProps) {
  const showLockout = lockRemaining > 0
  const showAttempts = attempts > 0 && !showLockout

  return (
    <Screen id="login" active={active}>
      <div className="split">
        <LeftPanel>
          <div>
            <div className="dost-badge">
              <i className="ti ti-star"></i> DOST Region 7 — Academe
            </div>
            <div className="brand-lockup">
              <div className="brand-icon">
                <svg viewBox="0 0 24 24">
                  <path d="M12 2L22 7V17L12 22L2 17V7L12 2Z" />
                </svg>
              </div>
              <div className="brand-text">
                <div className="brand-name">
                  DASIG<em>Connect</em>
                </div>
                <div className="brand-tag">Content Coordination Platform</div>
              </div>
            </div>
          </div>
          <div className="brand-footer-part">
            <div className="l-headline">
              One platform.
              <br />
              Every <em>institution.</em>
              <br />
              One Facebook page.
            </div>
            <div className="l-desc">
              DASIGConnect brings together DOST Region 7 member schools into a
              single structured workflow — from content submission to Facebook
              publishing.
            </div>
            <div className="l-features">
              <div className="l-feat">
                <div className="l-feat-icon">
                  <i className="ti ti-photo-up"></i>
                </div>
                <div className="l-feat-text">
                  <div className="l-feat-title">
                    Multi-institution Content Submission
                  </div>
                  <div className="l-feat-sub">
                    Contributors from each HEI submit event photos, videos, and
                    captions in one place.
                  </div>
                </div>
              </div>
              <div className="l-feat">
                <div className="l-feat-icon">
                  <i className="ti ti-clipboard-check"></i>
                </div>
                <div className="l-feat-text">
                  <div className="l-feat-title">Validation Workflow</div>
                  <div className="l-feat-sub">
                    Validators review and approve content before it reaches the
                    scheduler.
                  </div>
                </div>
              </div>
              <div className="l-feat">
                <div className="l-feat-icon">
                  <i className="ti ti-calendar-event"></i>
                </div>
                <div className="l-feat-text">
                  <div className="l-feat-title">AI-Assisted Scheduling</div>
                  <div className="l-feat-sub">
                    AI-generated captions and smart scheduling push content to
                    the DASIG Facebook page on time.
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div className="brand-footer-part">
            <div
              className="divider-text"
              style={{
                fontSize: 10,
                letterSpacing: '0.15em',
                textTransform: 'uppercase',
                color: 'var(--muted-2)',
                marginBottom: 10,
              }}
            >
              Member Institutions
            </div>
            <div className="l-members">
              <div className="member-pill">CIT-U</div>
              <div className="member-pill">Silliman University</div>
              <div className="member-pill">VSU</div>
              <div className="member-pill">USC</div>
              <div className="member-pill">UC</div>
              <div className="member-pill">+ others</div>
            </div>
          </div>
        </LeftPanel>
        <RightPanel>
          <div className="form-head">
            <div className="form-title">Welcome back.</div>
            <div className="form-desc">
              Sign in to your DASIGConnect workspace. Access is by invitation
              only.
            </div>
          </div>

          <div
            id="login-err-alert"
            className={`alert alert-err${loginError ? '' : ' hidden'}`}
          >
            <i className="ti ti-alert-circle"></i>
            <span id="login-err-msg">{loginError}</span>
          </div>

          <div id="lockout-box" className={`lockout-box${showLockout ? '' : ' hidden'}`}>
            <i className="ti ti-lock lock-ico"></i>
            <div className="lockout-title">Account Temporarily Locked</div>
            <div className="lockout-timer" id="lockout-timer">
              {formatTimer(lockRemaining)}
            </div>
            <div className="lockout-desc">
              5 consecutive failed attempts detected within 15 minutes. Your
              account is locked. A security alert has been dispatched to your
              registered email.
            </div>
          </div>

          <div id="login-fields" className={showLockout ? 'hidden' : ''}>
            <div className="fgroup">
              <label className="flabel">Institutional Email</label>
              <input
                id="l-email"
                className={`finput${loginError ? ' err' : ''}`}
                type="email"
                placeholder="yourname@institution.edu.ph"
                autoComplete="email"
                value={email}
                onChange={(event) => onEmailChange(event.target.value)}
              />
            </div>
            <div className="fgroup">
              <label className="flabel">
                Password
                <button type="button" className="flabel-action" onClick={onForgot}>
                  Forgot password?
                </button>
              </label>
              <div className="pw-wrap">
                <input
                  id="l-pw"
                  className={`finput${loginError ? ' err' : ''}`}
                  type={showPassword ? 'text' : 'password'}
                  placeholder="••••••••••"
                  autoComplete="current-password"
                  value={password}
                  onChange={(event) => onPasswordChange(event.target.value)}
                />
                <button
                  type="button"
                  className="eye-btn"
                  onClick={onTogglePassword}
                  aria-label="Show/hide password"
                >
                  <i className={showPassword ? 'ti ti-eye' : 'ti ti-eye-off'}></i>
                </button>
              </div>
            </div>

            <div id="attempt-row" className={`attempt-row${showAttempts ? '' : ' hidden'}`}>
              {Array.from({ length: 5 }).map((_, index) => (
                <div
                  key={`attempt-${index + 1}`}
                  className={`adot${index < attempts ? ' used' : ''}`}
                ></div>
              ))}
              <span className="attempt-label" id="attempt-label">
                {attempts} of 5 attempts
              </span>
            </div>

            <button
              type="button"
              className="btn-primary"
              onClick={onLogin}
              disabled={loading}
            >
              <i className="ti ti-login"></i> {loading ? 'Signing In...' : 'Sign In'}
            </button>
          </div>

          <div id="lockout-actions" className={showLockout ? '' : 'hidden'}>
            <button type="button" className="btn-ghost" onClick={onRequestReset}>
              <i className="ti ti-key" style={{ marginRight: 6 }}></i>Request
              Password Reset
            </button>
          </div>

          <div className="form-footer" style={{ marginTop: 20 }}>
            No account?{' '}
            <button type="button" className="btn-text" onClick={onNoAccount}>
              Learn how to get access
            </button>
          </div>
        </RightPanel>
      </div>
    </Screen>
  )
}

function formatTimer(seconds: number) {
  const safeSeconds = Math.max(seconds, 0)
  const minutes = Math.floor(safeSeconds / 60)
  const remaining = safeSeconds % 60
  return `${minutes.toString().padStart(2, '0')}:${remaining
    .toString()
    .padStart(2, '0')}`
}
