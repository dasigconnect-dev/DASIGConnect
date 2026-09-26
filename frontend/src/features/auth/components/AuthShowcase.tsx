import { useState, useEffect } from 'react'
import dasigLogo from '../../../assets/dasigconnect-logo.png'
import { listPublicInstitutions } from '../../../api/authApi'

interface AuthShowcaseProps {
  mode?: string
  institutions?: Array<{
    id: string | number
    name: string
    institutionCode?: string
    code?: string
    emailDomain?: string
  }>
  loadingInstitutions?: boolean
}

export default function AuthShowcase({
  institutions: propInstitutions,
  loadingInstitutions,
}: AuthShowcaseProps) {
  const [internalInstitutions, setInternalInstitutions] = useState<
    Array<{
      id: string | number
      name: string
      institutionCode?: string
      code?: string
      emailDomain?: string
    }>
  >([])
  const [internalLoading, setInternalLoading] = useState(false)

  useEffect(() => {
    // If institutions are explicitly provided via props, no need to fetch
    if (propInstitutions !== undefined) return

    let mounted = true
    const controller = new AbortController()
    setInternalLoading(true)

    listPublicInstitutions(controller.signal)
      .then((res) => {
        if (mounted && Array.isArray(res.data)) {
          setInternalInstitutions(res.data)
        }
      })
      .catch(() => {
        if (mounted) {
          setInternalInstitutions([])
        }
      })
      .finally(() => {
        if (mounted) {
          setInternalLoading(false)
        }
      })

    return () => {
      mounted = false
      controller.abort()
    }
  }, [propInstitutions])

  const institutionsList =
    propInstitutions !== undefined ? propInstitutions : internalInstitutions
  const isLoading =
    propInstitutions !== undefined
      ? Boolean(loadingInstitutions)
      : internalLoading

  return (
    <div className="auth-showcase-container">
      {/* Top Brand Header */}
      <div className="auth-showcase-top">
        <div className="brand-lockup">
          <div className="brand-icon">
            <img src={dasigLogo} alt="DASIGConnect logo" />
          </div>
          <div className="brand-text">
            <div className="brand-name">
              DASIG<em>Connect</em>
            </div>
            <div className="brand-tag">Content Coordination Platform</div>
          </div>
        </div>
      </div>

      {/* Minimal Mission Headline */}
      <div className="auth-showcase-mission">
        <h2 className="auth-mission-title">
          Harmonizing Visayas <em>Innovation.</em>
        </h2>
        <p className="auth-mission-subtitle">
          Synchronized editorial coordination connecting research institutions across Region 7.
        </p>
      </div>

      {/* Member Institutions Pill Section */}
      <div className="brand-footer-part">
        <div className="divider-text">
          Member Institutions
        </div>

        {isLoading ? (
          <div className="l-members">
            <span className="member-pill skeleton">...</span>
          </div>
        ) : institutionsList.length > 0 ? (
          <div className="l-members">
            {institutionsList.slice(0, 5).map((inst) => {
              const label =
                inst.institutionCode ||
                inst.code ||
                inst.emailDomain ||
                inst.name
              return (
                <div
                  key={inst.id}
                  className="member-pill"
                  title={inst.name}
                >
                  {label}
                </div>
              )
            })}
            {institutionsList.length > 5 && (
              <div className="member-pill others" title="Additional member institutions">
                + others
              </div>
            )}
          </div>
        ) : (
          <div className="no-institutions-label">
            No institutions yet
          </div>
        )}
      </div>
    </div>
  )
}
