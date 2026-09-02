import type { FormEvent } from 'react'
import Screen from '../../components/layout/Screen'
import LeftPanel from '../../components/layout/LeftPanel'
import RightPanel from '../../components/layout/RightPanel'
import { getPasswordRules } from '../../lib/passwordPolicy'

interface ResetPasswordScreenProps {
  active: boolean
  password: string
  confirmPassword: string
  showPassword: boolean
  showConfirmPassword: boolean
  loading: boolean
  error: string
  success: boolean
  onPasswordChange: (value: string) => void
  onConfirmPasswordChange: (value: string) => void
  onTogglePassword: () => void
  onToggleConfirmPassword: () => void
  onSubmit: () => void
  onBack: () => void
}

export default function ResetPasswordScreen({
  active,
  password,
  confirmPassword,
  showPassword,
  showConfirmPassword,
  loading,
  error,
  success,
  onPasswordChange,
  onConfirmPasswordChange,
  onTogglePassword,
  onToggleConfirmPassword,
  onSubmit,
  onBack,
}: ResetPasswordScreenProps) {
  const passwordsMatch = confirmPassword.length > 0 && password === confirmPassword
  const rules = getPasswordRules(password)
  const passwordOk = Object.values(rules).every(Boolean)
  const canSubmit = passwordOk && passwordsMatch && !loading

  return (
    <Screen id="reset-password" active={active}>
      <div className="split">
        <LeftPanel>
          <div>
            <div className="dost-badge">
              <i className="ti ti-star"></i> DOST Region 7 - Academe
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
              Set a new <em>password.</em>
            </div>
            <div className="l-desc">
              Reset links are single-use and time-limited. Once this password is
              saved, use it the next time you sign in.
            </div>
          </div>
        </LeftPanel>
        <RightPanel>
          <button type="button" className="back-btn" onClick={onBack}>
            <i className="ti ti-arrow-left"></i> Back to sign in
          </button>

          {success ? (
            <div className="success-center">
              <div className="success-icon">
                <i className="ti ti-circle-check"></i>
              </div>
              <div className="success-title">Password updated.</div>
              <div className="success-body">
                Your password has been reset. You can now sign in with the new
                password.
              </div>
              <button type="button" className="btn-primary" onClick={onBack}>
                <i className="ti ti-login"></i> Continue to Sign In
              </button>
            </div>
          ) : (
            <form
              onSubmit={(event: FormEvent<HTMLFormElement>) => {
                event.preventDefault()
                onSubmit()
              }}
            >
              <div className="form-head">
                <div className="form-title">Reset password</div>
                <div className="form-desc">
                  Enter a new password for your DASIGConnect account.
                </div>
              </div>
              {error && (
                <div className="alert alert-err">
                  <i className="ti ti-alert-circle"></i>
                  <div>{error}</div>
                </div>
              )}
              <div className="fgroup">
                <label className="flabel" htmlFor="reset-new-password">New Password</label>
                <div className="pw-wrap">
                  <input
                    id="reset-new-password"
                    name="new-password"
                    className="finput"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                    value={password}
                    onChange={(event) => onPasswordChange(event.target.value)}
                  />
                  <button type="button" className="eye-btn" onClick={onTogglePassword} aria-label="Toggle">
                    <i className={showPassword ? 'ti ti-eye' : 'ti ti-eye-off'}></i>
                  </button>
                </div>
                <div className="pw-rules">
                  <div className={`pw-rule${rules.length ? ' pass' : ''}`}>
                    <i className={rules.length ? 'ti ti-circle-check' : 'ti ti-circle'}></i> 12+ characters
                  </div>
                  <div className={`pw-rule${rules.upper ? ' pass' : ''}`}>
                    <i className={rules.upper ? 'ti ti-circle-check' : 'ti ti-circle'}></i> Uppercase letter
                  </div>
                  <div className={`pw-rule${rules.lower ? ' pass' : ''}`}>
                    <i className={rules.lower ? 'ti ti-circle-check' : 'ti ti-circle'}></i> Lowercase letter
                  </div>
                  <div className={`pw-rule${rules.number ? ' pass' : ''}`}>
                    <i className={rules.number ? 'ti ti-circle-check' : 'ti ti-circle'}></i> Number
                  </div>
                  <div className={`pw-rule${rules.symbol ? ' pass' : ''}`}>
                    <i className={rules.symbol ? 'ti ti-circle-check' : 'ti ti-circle'}></i> Special character
                  </div>
                  <div className={`pw-rule${rules.noSpaces ? ' pass' : ''}`}>
                    <i className={rules.noSpaces ? 'ti ti-circle-check' : 'ti ti-circle'}></i> No spaces
                  </div>
                  <div className={`pw-rule${rules.notCommon ? ' pass' : ''}`}>
                    <i className={rules.notCommon ? 'ti ti-circle-check' : 'ti ti-circle'}></i> Not common or sequential
                  </div>
                </div>
              </div>
              <div className="fgroup">
                <label className="flabel" htmlFor="reset-confirm-password">Confirm Password</label>
                <div className="pw-wrap">
                  <input
                    id="reset-confirm-password"
                    name="confirm-new-password"
                    className={`finput ${confirmPassword && !passwordsMatch ? 'err' : ''}`}
                    type={showConfirmPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                    value={confirmPassword}
                    onChange={(event) => onConfirmPasswordChange(event.target.value)}
                  />
                  <button type="button" className="eye-btn" onClick={onToggleConfirmPassword} aria-label="Toggle">
                    <i className={showConfirmPassword ? 'ti ti-eye' : 'ti ti-eye-off'}></i>
                  </button>
                </div>
              </div>
              <button
                type="submit"
                className="btn-primary"
                disabled={!canSubmit}
                aria-busy={loading}
              >
                <i className={`ti ${loading ? 'ti-loader-2 auth-btn-spinner' : 'ti-key'}`}></i>
                {loading ? 'Updating...' : 'Update Password'}
              </button>
            </form>
          )}
        </RightPanel>
      </div>
    </Screen>
  )
}
