import Screen from '../../components/layout/Screen'
import LeftPanel from '../../components/layout/LeftPanel'
import RightPanel from '../../components/layout/RightPanel'

interface ForgotSentScreenProps {
  active: boolean
  email: string
  onBack: () => void
}

export default function ForgotSentScreen({
  active,
  email,
  onBack,
}: ForgotSentScreenProps) {
  const displayEmail = email || 'yourname@institution.edu.ph'
  return (
    <Screen id="forgot-sent" active={active}>
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
              Check your <em>inbox.</em>
            </div>
            <div className="l-desc">
              If your email is registered, a reset link is on its way. The token
              expires in 60 minutes and invalidates any previous requests.
            </div>
          </div>
        </LeftPanel>
        <RightPanel>
          <div className="success-center">
            <div className="success-icon">
              <i className="ti ti-mail-check"></i>
            </div>
            <div className="success-title">Reset email sent.</div>
            <div className="success-body">
              If <strong style={{ color: 'var(--blue)' }}>{displayEmail}</strong> is
              registered in DASIGConnect, a password reset link has been sent.
              Check your spam folder if it doesn't arrive shortly.
              <br />
              <br />
              <span style={{ fontSize: 12, color: 'var(--muted-2)' }}>
                The reset link is single-use. Setting a new password will
                invalidate all other active sessions.
              </span>
            </div>
            <button type="button" className="btn-primary" onClick={onBack}>
              <i className="ti ti-arrow-left"></i> Back to Sign In
            </button>
            <div className="countdown">
              <i className="ti ti-clock"></i> Reset link expires in 60 minutes
            </div>
          </div>
        </RightPanel>
      </div>
    </Screen>
  )
}
