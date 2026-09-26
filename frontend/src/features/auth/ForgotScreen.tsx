import type { FormEvent } from 'react'
import Screen from '../../components/layout/Screen'
import LeftPanel from '../../components/layout/LeftPanel'
import RightPanel from '../../components/layout/RightPanel'
import AuthShowcase from './components/AuthShowcase'

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
          <AuthShowcase mode="recovery" />
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
          <form
            onSubmit={(event: FormEvent<HTMLFormElement>) => {
              event.preventDefault()
              onSubmit()
            }}
          >
            <div className="fgroup">
              <label className="flabel" htmlFor="forgot-email">Institutional Email</label>
              <input
                id="forgot-email"
                name="email"
                className="finput"
                type="email"
                autoComplete="email"
                placeholder="yourname@institution.edu.ph"
                value={email}
                onChange={(event) => onEmailChange(event.target.value)}
              />
            </div>
            <button
              type="submit"
              className="btn-primary"
              disabled={loading}
              aria-busy={loading}
            >
              <i className={`ti ${loading ? 'ti-loader-2 auth-btn-spinner' : 'ti-send'}`}></i>
              {loading ? 'Sending...' : 'Send Reset Link'}
            </button>
          </form>
          <button type="button" className="btn-ghost btn-cancel" onClick={onBack}>
            Cancel
          </button>
        </RightPanel>
      </div>
    </Screen>
  )
}
