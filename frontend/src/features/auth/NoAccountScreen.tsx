import Screen from '../../components/layout/Screen'
import LeftPanel from '../../components/layout/LeftPanel'
import RightPanel from '../../components/layout/RightPanel'

interface NoAccountScreenProps {
  active: boolean
  onBack: () => void
}

export default function NoAccountScreen({
  active,
  onBack,
}: NoAccountScreenProps) {
  return (
    <Screen id="no-account" active={active}>
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
              Invitation-only <em>access.</em>
            </div>
            <div className="l-desc">
              DASIGConnect is a closed platform for DOST Region 7 DASIG member
              institutions. All accounts are provisioned by Administrators.
            </div>
          </div>
        </LeftPanel>
        <RightPanel>
          <button type="button" className="back-btn" onClick={onBack}>
            <i className="ti ti-arrow-left"></i> Back to sign in
          </button>
          <div className="form-head">
            <div className="form-title">How to get access.</div>
            <div className="form-desc">
              DASIGConnect does not have self-registration. Accounts are created
              by the DASIG Administrator and activated by you via invitation
              email.
            </div>
          </div>
          <div className="alert alert-info">
            <i className="ti ti-info-circle"></i>
            <div>
              If you are a member of a DASIG HEI, contact your institution's
              Validator or the DASIG Administrator with your official
              institutional email address.
            </div>
          </div>
          <div
            style={{
              background: 'var(--ink-3)',
              border: '1px solid var(--border-2)',
              borderRadius: 'var(--r)',
              padding: 18,
              marginBottom: 16,
            }}
          >
            <div className="flabel" style={{ marginBottom: 14, display: 'block' }}>
              How account provisioning works
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div
                style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}
              >
                <div
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: '50%',
                    background: 'var(--blue-dim)',
                    border: '1px solid var(--blue-border)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                    fontSize: 11,
                    fontWeight: 700,
                    color: 'var(--blue)',
                  }}
                >
                  1
                </div>
                <div style={{ fontSize: 13, color: 'var(--muted)', lineHeight: 1.5 }}>
                  Administrator creates your account and dispatches an invitation
                  email to your institutional address.
                </div>
              </div>
              <div
                style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}
              >
                <div
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: '50%',
                    background: 'var(--blue-dim)',
                    border: '1px solid var(--blue-border)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                    fontSize: 11,
                    fontWeight: 700,
                    color: 'var(--blue)',
                  }}
                >
                  2
                </div>
                <div style={{ fontSize: 13, color: 'var(--muted)', lineHeight: 1.5 }}>
                  You click the activation link and set a secure password for your
                  account.
                </div>
              </div>
              <div
                style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}
              >
                <div
                  style={{
                    width: 24,
                    height: 24,
                    borderRadius: '50%',
                    background: 'var(--blue-dim)',
                    border: '1px solid var(--blue-border)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                    fontSize: 11,
                    fontWeight: 700,
                    color: 'var(--blue)',
                  }}
                >
                  3
                </div>
                <div style={{ fontSize: 13, color: 'var(--muted)', lineHeight: 1.5 }}>
                  Your account activates, and you're routed to your role-specific
                  workspace in DASIGConnect.
                </div>
              </div>
            </div>
          </div>
          <button type="button" className="btn-ghost" onClick={onBack}>
            Return to Sign In
          </button>
        </RightPanel>
      </div>
    </Screen>
  )
}
