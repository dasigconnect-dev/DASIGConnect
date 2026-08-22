import Screen from '../../components/layout/Screen'
import LeftPanel from '../../components/layout/LeftPanel'
import RightPanel from '../../components/layout/RightPanel'

type InviteState = 'form' | 'expired' | 'already' | 'success'

interface InviteRules {
  firstName: boolean
  lastName: boolean
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
  firstName: string
  lastName: string
  password: string
  confirmPassword: string
  rules: InviteRules
  inviteCountdown: string
  loading: boolean
  onFirstNameChange: (value: string) => void
  onLastNameChange: (value: string) => void
  onPasswordChange: (value: string) => void
  onConfirmPasswordChange: (value: string) => void
  onTogglePassword: () => void
  onToggleConfirmPassword: () => void
  onActivate: () => void
  onBackToLogin: () => void
  onResendExpired?: () => void
  resending?: boolean
  resendSuccess?: boolean
  showPassword: boolean
  showConfirmPassword: boolean
}

export default function InviteScreen({
  active,
  state,
  email,
  roleLabel,
  institution,
  firstName,
  lastName,
  password,
  confirmPassword,
  rules,
  inviteCountdown,
  loading,
  onFirstNameChange,
  onLastNameChange,
  onPasswordChange,
  onConfirmPasswordChange,
  onTogglePassword,
  onToggleConfirmPassword,
  onActivate,
  onBackToLogin,
  onResendExpired,
  resending = false,
  resendSuccess = false,
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
              Complete your <em>DASIG</em> profile.
            </div>
            <div className="l-desc">
              This invitation activates your account and completes your
              profile so DASIGConnect can show proper human names across your
              institution workspace.
            </div>
          </div>
          <div className="brand-footer-part">
            <div className="l-features">
              <div className="l-feat">
                <div className="l-feat-icon">
                  <i className="ti ti-lock-check"></i>
                </div>
                <div className="l-feat-text">
                  <div className="l-feat-title">Profile + Security</div>
                  <div className="l-feat-sub">
                    Add your first and last name, then set a password with
                    uppercase, number, and special character protection.
                  </div>
                </div>
              </div>
            </div>
          </div>
        </LeftPanel>
        <RightPanel>
          <div id="inv-expired" className={state === 'expired' ? '' : 'hidden'}>
            <div className="alert alert-err" style={{ marginBottom: 14 }}>
              <i className="ti ti-clock-x"></i>
              <div>
                <strong
                  style={{ display: 'block', marginBottom: 3, color: '#FCA5A5' }}
                >
                  Invitation link has expired.
                </strong>
                This invitation token is no longer valid. Your account remains
                in PENDING status. You can request a fresh invitation link below,
                or contact your DASIG Administrator.
              </div>
            </div>
            {resendSuccess && (
              <div className="alert alert-ok" style={{ marginBottom: 14 }}>
                <i className="ti ti-mail-check"></i>
                <div>
                  <strong style={{ display: 'block', marginBottom: 3, color: '#86EFAC' }}>
                    New invitation link sent!
                  </strong>
                  A fresh invitation link has been dispatched to your email address. Please check your inbox.
                </div>
              </div>
            )}
            {onResendExpired && !resendSuccess && (
              <button
                type="button"
                className="btn-primary"
                onClick={onResendExpired}
                disabled={resending}
                style={{ width: '100%', marginBottom: 10 }}
              >
                <i className="ti ti-send"></i> {resending ? 'Sending new link…' : 'Resend Invitation Link'}
              </button>
            )}
            <button
              type="button"
              className="btn-ghost"
              onClick={onBackToLogin}
              style={{ width: '100%' }}
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
                <div className="step-lbl">Profile</div>
              </div>
              <div className="step">
                <div className="step-connector"></div>
                <div className="step-dot">3</div>
                <div className="step-lbl">Active</div>
              </div>
            </div>

            <div className="token-box">
              <div className="token-lbl">Activating account for</div>
              {email ? (
                <>
                  <div className="token-email" id="inv-email-display">
                    {email}
                  </div>
                  <div className="token-meta">
                    {roleLabel && <span id="inv-role-display">{roleLabel}</span>}
                    {roleLabel && institution && <span className="token-dot"></span>}
                    {institution && <span id="inv-inst-display">{institution}</span>}
                  </div>
                </>
              ) : (
                <div className="token-meta" style={{ marginTop: '4px' }}>
                  <span>Loading invitation details...</span>
                </div>
              )}
            </div>

            <div className="form-head compact">
              <div className="form-title">Activate your account</div>
              <div className="form-desc">
                This invitation activates your account and completes your profile.
              </div>
            </div>

            <div className="profile-grid">
              <div className="fgroup">
                <label className="flabel" htmlFor="inv-first-name">First Name</label>
                <input
                  id="inv-first-name"
                  className={`finput${
                    firstName.length === 0 ? '' : rules.firstName ? ' good' : ' err'
                  }`}
                  type="text"
                  autoComplete="off"
                  placeholder="First name"
                  value={firstName}
                  onChange={(event) => onFirstNameChange(event.target.value)}
                  aria-invalid={firstName.length > 0 && !rules.firstName}
                />
                <div className={`field-hint${firstName.length > 0 && !rules.firstName ? ' err' : ''}`}>
                  Letters, spaces, hyphens, and apostrophes only.
                </div>
              </div>

              <div className="fgroup">
                <label className="flabel" htmlFor="inv-last-name">Last Name</label>
                <input
                  id="inv-last-name"
                  className={`finput${
                    lastName.length === 0 ? '' : rules.lastName ? ' good' : ' err'
                  }`}
                  type="text"
                  autoComplete="off"
                  placeholder="Last name"
                  value={lastName}
                  onChange={(event) => onLastNameChange(event.target.value)}
                  aria-invalid={lastName.length > 0 && !rules.lastName}
                />
                <div className={`field-hint${lastName.length > 0 && !rules.lastName ? ' err' : ''}`}>
                  Required for your DASIGConnect profile.
                </div>
              </div>
            </div>

            <div className="fgroup">
              <label className="flabel">Create Password</label>
              <div className="pw-wrap">
                <input
                  id="inv-pw"
                  className="finput"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="new-password"
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
                  autoComplete="new-password"
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
                !(
                  rules.firstName &&
                  rules.lastName &&
                  rules.length &&
                  rules.upper &&
                  rules.number &&
                  rules.symbol &&
                  rules.match
                )
              }
            >
              <i className="ti ti-circle-check"></i>{' '}
              {loading ? 'Activating...' : 'Activate My Account'}
            </button>
            {inviteCountdown && (
              <div className="countdown">
                <i className="ti ti-clock"></i>{' '}
                <span id="inv-countdown">{inviteCountdown}</span>
              </div>
            )}
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
                <div className="step-lbl">Profile</div>
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
                will be redirected to your dashboard.
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
