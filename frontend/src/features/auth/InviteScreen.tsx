import Screen from '../../components/layout/Screen'
import LeftPanel from '../../components/layout/LeftPanel'
import RightPanel from '../../components/layout/RightPanel'

type InviteState = 'form' | 'expired' | 'already' | 'success'

interface InviteRules {
  length: boolean
  upper: boolean
  number: boolean
  symbol: boolean
  match: boolean
}

interface InviteScreenProps {
  active: boolean
  state: InviteState
  email: string
  roleLabel: string
  institution: string
  password: string
  confirmPassword: string
  rules: InviteRules
  inviteCountdown: string
  loading: boolean
  onPasswordChange: (value: string) => void
  onConfirmPasswordChange: (value: string) => void
  onTogglePassword: () => void
  onToggleConfirmPassword: () => void
  onActivate: () => void
  onBackToLogin: () => void
  showPassword: boolean
  showConfirmPassword: boolean
}

export default function InviteScreen({
  active,
  state,
  email,
  roleLabel,
  institution,
  password,
  confirmPassword,
  rules,
  inviteCountdown,
  loading,
  onPasswordChange,
  onConfirmPasswordChange,
  onTogglePassword,
  onToggleConfirmPassword,
  onActivate,
  onBackToLogin,
  showPassword,
  showConfirmPassword,
}: InviteScreenProps) {
  return (
    <Screen id="invite" active={active}>
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
              You've been <em>invited</em> to join DASIG.
            </div>
            <div className="l-desc">
              Set a secure password to activate your account and begin
              contributing to the DASIG social media content workflow for your
              institution.
            </div>
          </div>
          <div className="brand-footer-part">
            <div className="l-features">
              <div className="l-feat">
                <div className="l-feat-icon">
                  <i className="ti ti-lock-check"></i>
                </div>
                <div className="l-feat-text">
                  <div className="l-feat-title">Password Requirements</div>
                  <div className="l-feat-sub">
                    Minimum 8 characters with uppercase, number, and special
                    character for account security.
                  </div>
                </div>
              </div>
            </div>
          </div>
        </LeftPanel>
        <RightPanel>
          <div id="inv-expired" className={state === 'expired' ? '' : 'hidden'}>
            <div className="alert alert-err" style={{ marginBottom: 0 }}>
              <i className="ti ti-clock-x"></i>
              <div>
                <strong
                  style={{ display: 'block', marginBottom: 3, color: '#FCA5A5' }}
                >
                  Invitation link has expired.
                </strong>
                This invitation token is no longer valid. Your account remains
                in PENDING status. Please contact your institution's Validator
                or the DASIG Administrator to request a new invitation.
              </div>
            </div>
            <button
              type="button"
              className="btn-ghost"
              onClick={onBackToLogin}
              style={{ marginTop: 14 }}
            >
              Return to Sign In
            </button>
          </div>

          <div id="inv-already" className={state === 'already' ? '' : 'hidden'}>
            <div className="alert alert-warn" style={{ marginBottom: 0 }}>
              <i className="ti ti-alert-triangle"></i>
              <div>
                <strong
                  style={{ display: 'block', marginBottom: 3, color: '#FCD34D' }}
                >
                  Account already activated.
                </strong>
                This invitation link has already been used. Your account is
                active — please sign in to access your DASIGConnect workspace.
              </div>
            </div>
            <button
              type="button"
              className="btn-primary"
              onClick={onBackToLogin}
              style={{ marginTop: 14 }}
            >
              <i className="ti ti-login"></i> Go to Sign In
            </button>
          </div>

          <div id="inv-form" className={state === 'form' ? '' : 'hidden'}>
            <div className="steps">
              <div className="step done">
                <div className="step-connector"></div>
                <div className="step-dot">
                  <i className="ti ti-check"></i>
                </div>
                <div className="step-lbl">Invited</div>
              </div>
              <div className="step active">
                <div className="step-connector"></div>
                <div className="step-dot">2</div>
                <div className="step-lbl">Set Password</div>
              </div>
              <div className="step">
                <div className="step-connector"></div>
                <div className="step-dot">3</div>
                <div className="step-lbl">Active</div>
              </div>
            </div>

            <div className="token-box">
              <div className="token-lbl">Activating account for</div>
              <div className="token-email" id="inv-email-display">
                {email}
              </div>
              <div className="token-meta">
                <span id="inv-role-display">{roleLabel}</span>
                <span className="token-dot"></span>
                <span id="inv-inst-display">{institution}</span>
              </div>
            </div>

            <div className="fgroup">
              <label className="flabel">Create Password</label>
              <div className="pw-wrap">
                <input
                  id="inv-pw"
                  className="finput"
                  type={showPassword ? 'text' : 'password'}
                  placeholder="Create a strong password"
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
              <div className="pw-rules">
                <div className={`pw-rule${rules.length ? ' pass' : ''}`} id="r-len">
                  <i
                    className={rules.length ? 'ti ti-circle-check' : 'ti ti-circle'}
                  ></i>{' '}
                  8+ characters
                </div>
                <div className={`pw-rule${rules.upper ? ' pass' : ''}`} id="r-up">
                  <i
                    className={rules.upper ? 'ti ti-circle-check' : 'ti ti-circle'}
                  ></i>{' '}
                  Uppercase letter
                </div>
                <div className={`pw-rule${rules.number ? ' pass' : ''}`} id="r-num">
                  <i
                    className={rules.number ? 'ti ti-circle-check' : 'ti ti-circle'}
                  ></i>{' '}
                  Number
                </div>
                <div className={`pw-rule${rules.symbol ? ' pass' : ''}`} id="r-sym">
                  <i
                    className={rules.symbol ? 'ti ti-circle-check' : 'ti ti-circle'}
                  ></i>{' '}
                  Special character
                </div>
              </div>
            </div>
            <div className="fgroup">
              <label className="flabel">Confirm Password</label>
              <div className="pw-wrap">
                <input
                  id="inv-pw2"
                  className={`finput${
                    confirmPassword.length === 0
                      ? ''
                      : rules.match
                        ? ' good'
                        : ' err'
                  }`}
                  type={showConfirmPassword ? 'text' : 'password'}
                  placeholder="Repeat your password"
                  value={confirmPassword}
                  onChange={(event) =>
                    onConfirmPasswordChange(event.target.value)
                  }
                />
                <button
                  type="button"
                  className="eye-btn"
                  onClick={onToggleConfirmPassword}
                  aria-label="Toggle"
                >
                  <i
                    className={showConfirmPassword ? 'ti ti-eye' : 'ti ti-eye-off'}
                  ></i>
                </button>
              </div>
              <div id="pw-match" style={{ fontSize: 12, marginTop: 6, color: rules.match ? 'var(--ok)' : 'var(--error)' }}>
                {confirmPassword.length > 0
                  ? rules.match
                    ? '✓ Passwords match'
                    : '✗ Passwords do not match'
                  : ''}
              </div>
            </div>

            <button
              id="inv-btn"
              type="button"
              className="btn-primary"
              onClick={onActivate}
              disabled={
                loading ||
                !(rules.length && rules.upper && rules.number && rules.symbol && rules.match)
              }
            >
              <i className="ti ti-circle-check"></i>{' '}
              {loading ? 'Activating...' : 'Activate My Account'}
            </button>
            <div className="countdown">
              <i className="ti ti-clock"></i>{' '}
              <span id="inv-countdown">{inviteCountdown}</span>
            </div>
          </div>

          <div id="inv-success" className={state === 'success' ? '' : 'hidden'}>
            <div className="steps">
              <div className="step done">
                <div className="step-connector"></div>
                <div className="step-dot">
                  <i className="ti ti-check"></i>
                </div>
                <div className="step-lbl">Invited</div>
              </div>
              <div className="step done">
                <div className="step-connector"></div>
                <div className="step-dot">
                  <i className="ti ti-check"></i>
                </div>
                <div className="step-lbl">Set Password</div>
              </div>
              <div className="step done">
                <div className="step-connector"></div>
                <div className="step-dot">
                  <i className="ti ti-check"></i>
                </div>
                <div className="step-lbl">Active</div>
              </div>
            </div>
            <div className="success-center">
              <div className="success-icon">
                <i className="ti ti-circle-check"></i>
              </div>
              <div className="success-title">Account activated!</div>
              <div className="success-body">
                Your DASIGConnect account is now active and bound to your
                institution's workspace. A confirmation email has been sent. You
                can now sign in.
              </div>
              <button type="button" className="btn-primary" onClick={onBackToLogin}>
                <i className="ti ti-login"></i> Proceed to Sign In
              </button>
            </div>
          </div>
        </RightPanel>
      </div>
    </Screen>
  )
}
