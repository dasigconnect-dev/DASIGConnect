import type { FormEvent } from 'react'
import { useMemo, useState } from 'react'
import Screen from '../../components/layout/Screen'
import LeftPanel from '../../components/layout/LeftPanel'
import RightPanel from '../../components/layout/RightPanel'
import AuthShowcase from './components/AuthShowcase'
import { isInAppBrowser } from '../../utils/inAppBrowser'

type InviteState = 'form' | 'expired' | 'already' | 'success'

interface InviteRules {
  firstName: boolean
  lastName: boolean
  length: boolean
  upper: boolean
  lower: boolean
  number: boolean
  symbol: boolean
  noSpaces: boolean
  notCommon: boolean
  noIdentity: boolean
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
  showPassword,
  showConfirmPassword,
}: InviteScreenProps) {
  const showInAppBrowserNotice = useMemo(() => isInAppBrowser(), [])
  const [linkCopied, setLinkCopied] = useState(false)

  async function handleCopyLink() {
    try {
      await navigator.clipboard.writeText(window.location.href)
      setLinkCopied(true)
      setTimeout(() => setLinkCopied(false), 3000)
    } catch {
      // Clipboard API unavailable in this in-app browser — the visible URL
      // bar (if any) is the fallback for a manual copy.
    }
  }

  return (
    <Screen id="invite" active={active}>
      <div className="split">
        <LeftPanel>
          <AuthShowcase mode="activation" />
        </LeftPanel>
        <RightPanel>
          {showInAppBrowserNotice && (
            <div className="alert alert-warn" style={{ marginBottom: 14 }}>
              <i className="ti ti-alert-triangle"></i>
              <div>
                <strong style={{ display: 'block', marginBottom: 3 }}>
                  You're viewing this inside an app's built-in browser.
                </strong>
                Some apps (Messenger, Instagram, etc.) reuse this popup across
                links and can show an outdated page. For the most reliable
                activation, open this link in Chrome or your device's default
                browser instead.
                <div style={{ marginTop: 8 }}>
                  <button
                    type="button"
                    className="btn-ghost"
                    onClick={() => void handleCopyLink()}
                  >
                    <i className="ti ti-copy"></i>{' '}
                    {linkCopied ? 'Link copied!' : 'Copy this link'}
                  </button>
                </div>
              </div>
            </div>
          )}
          <div id="inv-expired" className={state === 'expired' ? '' : 'hidden'}>
            <div className="alert alert-err" style={{ marginBottom: 14 }}>
              <i className="ti ti-clock-x"></i>
              <div>
                <strong
                  style={{ display: 'block', marginBottom: 3 }}
                >
                  Invitation link has expired.
                </strong>
                This invitation token is no longer valid. Your account remains
                in PENDING status. If an Administrator or Moderator already
                sent you a newer invitation, make sure you open{' '}
                <strong>that latest email's link</strong> in a fresh browser
                tab or window — reopening this same link (especially from an
                app's built-in browser popup) will keep showing this expired
                page even after a new one is issued. Otherwise, contact your
                DASIG Administrator or Moderator to request a new invitation
                link.
              </div>
            </div>
            <button
              type="button"
              className="btn-primary"
              onClick={onBackToLogin}
              style={{ width: '100%' }}
            >
              <i className="ti ti-login"></i> Return to Sign In
            </button>
          </div>

          <div id="inv-already" className={state === 'already' ? '' : 'hidden'}>
            <div className="alert alert-warn" style={{ marginBottom: 0 }}>
              <i className="ti ti-alert-triangle"></i>
              <div>
                <strong
                  style={{ display: 'block', marginBottom: 3 }}
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

          <form
            id="inv-form"
            className={state === 'form' ? '' : 'hidden'}
            onSubmit={(event: FormEvent<HTMLFormElement>) => {
              event.preventDefault()
              onActivate()
            }}
          >
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
                  name="given-name"
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
                  name="family-name"
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
              <label className="flabel" htmlFor="inv-pw">Create Password</label>
              <div className="pw-wrap">
                <input
                  id="inv-pw"
                  name="new-password"
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
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  <i className={showPassword ? 'ti ti-eye' : 'ti ti-eye-off'}></i>
                </button>
              </div>
              <div className="pw-rules">
                <div className={`pw-rule${rules.length ? ' pass' : ''}`} id="r-len">
                  <i
                    className={rules.length ? 'ti ti-circle-check' : 'ti ti-circle'}
                  ></i>{' '}
                  12+ characters
                </div>
                <div className={`pw-rule${rules.upper ? ' pass' : ''}`} id="r-up">
                  <i
                    className={rules.upper ? 'ti ti-circle-check' : 'ti ti-circle'}
                  ></i>{' '}
                  Uppercase letter
                </div>
                <div className={`pw-rule${rules.lower ? ' pass' : ''}`}>
                  <i
                    className={rules.lower ? 'ti ti-circle-check' : 'ti ti-circle'}
                  ></i>{' '}
                  Lowercase letter
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
                <div className={`pw-rule${rules.noSpaces ? ' pass' : ''}`}>
                  <i
                    className={rules.noSpaces ? 'ti ti-circle-check' : 'ti ti-circle'}
                  ></i>{' '}
                  No spaces
                </div>
                <div className={`pw-rule${rules.notCommon ? ' pass' : ''}`}>
                  <i
                    className={rules.notCommon ? 'ti ti-circle-check' : 'ti ti-circle'}
                  ></i>{' '}
                  Not common or sequential
                </div>
                <div className={`pw-rule${rules.noIdentity ? ' pass' : ''}`}>
                  <i
                    className={rules.noIdentity ? 'ti ti-circle-check' : 'ti ti-circle'}
                  ></i>{' '}
                  Does not include your name or email
                </div>
              </div>
            </div>
            <div className="fgroup">
              <label className="flabel" htmlFor="inv-pw2">Confirm Password</label>
              <div className="pw-wrap">
                <input
                  id="inv-pw2"
                  name="confirm-new-password"
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
                  aria-label={showConfirmPassword ? 'Hide confirm password' : 'Show confirm password'}
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
              type="submit"
              className="btn-primary"
              disabled={
                loading ||
                !(
                  rules.firstName &&
                  rules.lastName &&
                  rules.length &&
                  rules.upper &&
                  rules.lower &&
                  rules.number &&
                  rules.symbol &&
                  rules.noSpaces &&
                  rules.notCommon &&
                  rules.noIdentity &&
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
          </form>

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
