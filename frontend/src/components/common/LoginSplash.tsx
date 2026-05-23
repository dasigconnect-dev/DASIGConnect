import { useEffect, useState } from 'react'
import type { User } from '../../types/auth.types'

interface LoginSplashProps {
  user: User | null
  visible: boolean
}

const ROLE_LABEL: Record<string, string> = {
  admin: 'Administrator',
  validator: 'Validator',
  contributor: 'Contributor',
}

export default function LoginSplash({ user, visible }: LoginSplashProps) {
  const [mounted, setMounted] = useState(false)
  const [exiting, setExiting] = useState(false)

  useEffect(() => {
    if (visible) {
      setMounted(true)
      setExiting(false)
    } else if (mounted) {
      setExiting(true)
      const t = window.setTimeout(() => {
        setMounted(false)
        setExiting(false)
      }, 380)
      return () => window.clearTimeout(t)
    }
  }, [visible, mounted])

  if (!mounted) return null

  const roleLabel = user ? (ROLE_LABEL[user.role] ?? 'Member') : null
  const greeting = user?.name ? `Welcome back, ${user.name.split(' ')[0]}.` : 'Welcome to DASIGConnect.'

  return (
    <div
      className={`dc-login-splash${exiting ? ' exiting' : ''}`}
      role="status"
      aria-label="Signing in"
      aria-live="polite"
    >
      <div className="dc-splash-inner">
        <div className="dc-splash-logo">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 2L22 7V17L12 22L2 17V7L12 2Z" />
          </svg>
        </div>
        <div>
          <div className="dc-splash-brand">DASIG<em>Connect</em></div>
          <div className="dc-splash-tagline">Content Coordination Platform</div>
        </div>
        <div className="dc-splash-divider" aria-hidden="true" />
        <div className="dc-splash-greeting">
          <div className="dc-splash-welcome">{greeting}</div>
          {roleLabel && (
            <span className="dc-splash-role-pill">
              <i className="ti ti-user" aria-hidden="true" />
              {roleLabel}
            </span>
          )}
        </div>
        <i
          className="ti ti-loader-2 dc-splash-progress"
          aria-hidden="true"
        />
      </div>
    </div>
  )
}
