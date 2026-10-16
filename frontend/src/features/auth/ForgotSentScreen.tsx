interface ForgotSentScreenProps {
  active?: boolean
  email: string
  onBack: () => void
}

export default function ForgotSentScreen({
  email,
  onBack,
}: ForgotSentScreenProps) {
  const displayEmail = email || 'yourname@institution.edu.ph'
  return (
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
  )
}

