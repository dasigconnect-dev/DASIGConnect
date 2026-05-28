import Screen from '../../components/layout/Screen'
import LeftPanel from '../../components/layout/LeftPanel'
import RightPanel from '../../components/layout/RightPanel'

interface ForgotScreenProps {
  active: boolean
  email: string
  loading: boolean
  onEmailChange: (value: string) => void
  onSubmit: () => void
  onBack: () => void
}

export default function ForgotScreen({
  active,
  email,
  loading,
  onEmailChange,
  onSubmit,
  onBack,
}: ForgotScreenProps) {
  return (
    <Screen id="forgot" active={active}>
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
              Secure account <em>recovery.</em>
            </div>
            <div className="l-desc">
              We'll send a single-use, time-limited reset link to your registered
              institutional email. Any previous reset tokens are immediately
              invalidated for security.
            </div>
          </div>
          <div className="brand-footer-part">
            <div className="l-feat">
              <div className="l-feat-icon">
                <i className="ti ti-shield-lock"></i>
              </div>
              <div className="l-feat-text">
                <div className="l-feat-title">
                  Account Enumeration Protection
                </div>
                <div className="l-feat-sub">
                  We never reveal whether an email exists in our system — you'll
                  always see the same response.
                </div>
              </div>
            </div>
          </div>
        </LeftPanel>
        <RightPanel>
          <button type="button" className="back-btn" onClick={onBack}>
            <i className="ti ti-arrow-left"></i> Back to sign in
          </button>
          <div className="form-head">
            <div className="form-title">Forgot your password?</div>
            <div className="form-desc">
              Enter your registered institutional email. If a matching account
              exists, a reset link will be dispatched.
            </div>
          </div>
          <div className="alert alert-info">
            <i className="ti ti-shield-check"></i>
            <div>
              For security, we will not confirm whether this email address is
              registered in DASIGConnect.
            </div>
          </div>
          <div className="fgroup">
            <label className="flabel">Institutional Email</label>
            <input
              id="forgot-email"
              className="finput"
              type="email"
              placeholder="yourname@institution.edu.ph"
              value={email}
              onChange={(event) => onEmailChange(event.target.value)}
            />
          </div>
          <button
            type="button"
            className="btn-primary"
            onClick={onSubmit}
            disabled={loading}
            aria-busy={loading}
          >
            <i className={`ti ${loading ? 'ti-loader-2 auth-btn-spinner' : 'ti-send'}`}></i>
            {loading ? 'Sending...' : 'Send Reset Link'}
          </button>
          <button type="button" className="btn-ghost" onClick={onBack}>
            Cancel
          </button>
        </RightPanel>
      </div>
    </Screen>
  )
}
