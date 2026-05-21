import { useState, useEffect, type CSSProperties } from 'react'
import { useNavigate } from 'react-router-dom'
import Screen from '../../components/layout/Screen'
import type { User } from '../../types/auth.types'
import { createInstitution, getUserCounts, inviteUser, listInstitutions } from '../../api/authApi'
import { listSubmissions, type SubmissionSummary } from '../../api/submissionApi'

interface DashboardScreenProps {
  active: boolean
  user: User | null
  showBanner: boolean
  bannerTime: string
  showDropdown: boolean
  onToggleDropdown: () => void
  onDismissBanner: () => void
  onStayLoggedIn: () => void
  onLogout: () => void
}

interface StatItem {
  icon: string
  color: string
  label: string
  value: string
  highlight?: boolean
  valueStyle?: CSSProperties
}

interface ActionItem {
  icon: string
  accent: string
  title: string
  subtitle: string
  emphasized?: boolean
}

interface ActivityItem {
  title: string
  subtitle: string
  institution: string
  submitted: string
  status: {
    label: string
    icon: string
    className: string
  }
}

interface DashboardStats {
  submissions: SubmissionSummary[]
  contributors: number
  validators: number
}

const emptyContributorActivity: ActivityItem[] = []

export default function DashboardScreen({
  active,
  user,
  showBanner,
  bannerTime,
  showDropdown,
  onToggleDropdown,
  onDismissBanner,
  onStayLoggedIn,
  onLogout,
}: DashboardScreenProps) {
  const navigate = useNavigate()
  const [openModal, setOpenModal] = useState<'institution' | 'invite' | null>(null)
  const [institutions, setInstitutions] = useState<
    { id: string; name: string; code: string; emailDomain: string }[]
  >([])
  const [institutionsLoading, setInstitutionsLoading] = useState(false)
  const [institutionsError, setInstitutionsError] = useState('')
  const [dashboardStats, setDashboardStats] = useState<DashboardStats>({
    submissions: [],
    contributors: 0,
    validators: 0,
  })

  useEffect(() => {
    if (user?.role === 'validator' && user.inst) {
      setInstitutions([
        {
          id: user.inst,
          name: 'Your institution',
          code: '',
          emailDomain: '',
        },
      ])
      setInviteSelectedInstId(user.inst)
      setInviteRole('contributor')
      return
    }
    if (user?.role !== 'admin') return
    setInstitutionsLoading(true)
    setInstitutionsError('')
    listInstitutions()
      .then((response) => {
        const mapped = response.data.map((item) => ({
          id: item.id,
          name: item.name,
          code: item.institutionCode,
          emailDomain: item.emailDomain,
        }))
        setInstitutions(mapped)
      })
      .catch((err: any) => {
        setInstitutionsError(
          getApiErrorMessage(err, 'Unable to load institutions. Please refresh.'),
        )
      })
      .finally(() => setInstitutionsLoading(false))
  }, [user?.role])

  useEffect(() => {
    if (!user) return
    listSubmissions()
      .then((response) => {
        setDashboardStats((current) => ({ ...current, submissions: response.data }))
      })
      .catch(() => {
        setDashboardStats((current) => ({ ...current, submissions: [] }))
      })
  }, [user?.role, user?.inst])

  useEffect(() => {
    if (!user) return
    if (user.role === 'contributor') {
      setDashboardStats((current) => ({ ...current, contributors: 0, validators: 0 }))
      return
    }

    const institutionIds = user.role === 'admin'
      ? institutions.map((institution) => institution.id)
      : user.inst
        ? [user.inst]
        : []

    if (institutionIds.length === 0) {
      setDashboardStats((current) => ({ ...current, contributors: 0, validators: 0 }))
      return
    }

    let active = true
    Promise.all(institutionIds.map((institutionId) => getUserCounts(institutionId)))
      .then((responses) => {
        if (!active) return
        const totals = responses.reduce(
          (sum, response) => ({
            contributors: sum.contributors + response.data.contributors,
            validators: sum.validators + response.data.validators,
          }),
          { contributors: 0, validators: 0 },
        )
        setDashboardStats((current) => ({ ...current, ...totals }))
      })
      .catch(() => {
        if (active) {
          setDashboardStats((current) => ({ ...current, contributors: 0, validators: 0 }))
        }
      })

    return () => {
      active = false
    }
  }, [user?.role, user?.inst, institutions])

  const [instName, setInstName] = useState('')
  const [instDomain, setInstDomain] = useState('')
  const [instLoading, setInstLoading] = useState(false)
  const [instError, setInstError] = useState('')
  const [instSuccess, setInstSuccess] = useState<{ id: string; name: string; code: string } | null>(null)

  const [inviteRole, setInviteRole] = useState<'contributor' | 'validator'>('contributor')
  const [inviteEmailsText, setInviteEmailsText] = useState('')
  const [inviteSelectedInstId, setInviteSelectedInstId] = useState('')
  const [inviteLoading, setInviteLoading] = useState(false)
  const [inviteResults, setInviteResults] = useState<{
    total: number
    success: string[]
    failed: { email: string; reason: string }[]
  } | null>(null)

  useEffect(() => {
    if (institutions.length > 0 && !inviteSelectedInstId) {
      setInviteSelectedInstId(institutions[0].id)
    }
  }, [institutions, inviteSelectedInstId])

  const handleCloseModals = () => {
    setOpenModal(null)
    setInstName('')
    setInstDomain('')
    setInstError('')
    setInstSuccess(null)
    setInviteEmailsText('')
    setInviteResults(null)
  }

  const handleProvisionInstitution = async (e: React.FormEvent) => {
    e.preventDefault()
    setInstError('')
    setInstSuccess(null)

    const name = instName.trim()
    const domain = normalizeDomain(instDomain)
    const code = generateInstitutionCode(name, domain)

    if (!name || !domain) {
      setInstError('All fields are required.')
      return
    }

    if (!isValidDomain(domain)) {
      setInstError('Enter a valid email domain (e.g. su.edu.ph).')
      return
    }

    setInstLoading(true)
    try {
      const response = await createInstitution(name, code, domain)
      const newInst = {
        id: response.data.id,
        name: response.data.name,
        code: response.data.institutionCode,
      }
      const updated = [...institutions, { ...newInst, emailDomain: response.data.emailDomain }]
      setInstitutions(updated)
      setInviteSelectedInstId(newInst.id)
      setInstSuccess(newInst)
      setInstName('')
      setInstDomain('')
    } catch (err: any) {
      setInstError(
        getApiErrorMessage(err, 'An error occurred while provisioning the workspace.')
      )
    } finally {
      setInstLoading(false)
    }
  }

  const handleSendInvitations = async (e: React.FormEvent) => {
    e.preventDefault()
    setInviteResults(null)

    const rawEmails = inviteEmailsText
      .split(/[\s,\n]+/)
      .map((email) => email.trim())
      .filter((email) => email.length > 0)
    const inviteEmails = Array.from(new Set(rawEmails.map((email) => email.toLowerCase())))

    if (inviteEmails.length === 0) {
      alert('Please enter at least one recipient email address.')
      return
    }

    if (!inviteSelectedInstId) {
      alert('Please select or provision an institution first.')
      return
    }

    if (inviteEmails.length > 15) {
      setInviteResults({
        total: inviteEmails.length,
        success: [],
        failed: [
          {
            email: 'Batch Error',
            reason: 'The batch exceeds the maximum allowed size of 15 invitations. The entire batch is rejected.',
          },
        ],
      })
      return
    }

    setInviteLoading(true)

    const success: string[] = []
    const failed: { email: string; reason: string }[] = []

    for (const email of inviteEmails) {
      if (!isValidEmail(email)) {
        failed.push({
          email,
          reason: 'Enter a valid email address.',
        })
        continue
      }

      try {
        const response = await inviteUser({
          recipientEmail: email,
          institutionId: inviteSelectedInstId,
          assignedRole: inviteRole,
        })
        if (response.data.emailDelivered) {
          success.push(email)
        } else {
          failed.push({
            email,
            reason: `Account was provisioned, but the email was not delivered. Invite link: ${response.data.invitationUrl}`,
          })
        }
      } catch (err: any) {
        failed.push({
          email,
          reason: getApiErrorMessage(err, 'Invitation failed to process.'),
        })
      }
    }

    setInviteResults({
      total: inviteEmails.length,
      success,
      failed,
    })
    setInviteLoading(false)
  }

  const handleResubmitFailedOnly = () => {
    if (!inviteResults) return
    const failedEmails = inviteResults.failed.map((f) => f.email).join(', ')
    setInviteEmailsText(failedEmails)
    setInviteResults(null)
  }

  const handleActionClick = (title: string) => {
    if (title === 'Submit Event Content') {
      navigate('/submissions/new')
      return
    }
    if (title === 'Invite Users') {
      setOpenModal('invite')
      return
    }
    if (title === 'Manage Contributors') {
      setInviteRole('contributor')
      setOpenModal('invite')
      return
    }
    if (title === 'Add Institution') {
      setOpenModal('institution')
    }
  }
  return (
    <Screen id="dashboard" active={active}>
      <div id="screen-dashboard" style={{ background: 'var(--d-bg)' }}>
        <div id="session-banner" className={showBanner ? '' : 'hidden'}>
          <div className="banner-msg">
            <i className="ti ti-clock-exclamation"></i>
            <span>
              Your session expires in <strong id="banner-time">{bannerTime}</strong>
              . Stay logged in?
            </span>
          </div>
          <div className="banner-actions">
            <button type="button" className="banner-btn banner-btn-stay" onClick={onStayLoggedIn}>
              Stay Logged In
            </button>
            <button
              type="button"
              className="banner-btn banner-btn-dismiss"
              onClick={onDismissBanner}
            >
              Dismiss
            </button>
          </div>
        </div>

        <div style={{ display: 'flex', minHeight: '100vh' }}>
          {/* SIDEBAR NAVIGATION */}
          <aside className="dash-sidebar" id="dash-sidebar">
            <div className="sidebar-brand-wrapper">
              <div className="dash-brand">
                <div className="dash-brand-icon">
                  <svg viewBox="0 0 24 24" style={{ width: 18, height: 18, fill: 'white' }}>
                    <path d="M12 2L22 7V17L12 22L2 17V7L12 2Z" />
                  </svg>
                </div>
                <div className="dash-brand-name" style={{ marginLeft: 8 }}>
                  DASIG<em>Connect</em>
                </div>
              </div>
            </div>

            <div className="sidebar-nav">
              <button className="sidebar-link active">
                <i className="ti ti-layout-dashboard"></i> Dashboard
              </button>
              <button
                className="sidebar-link"
                id="side-nav-submit"
                style={{ display: navVisibility(user).submit ? 'flex' : 'none' }}
                onClick={() => {
                  if (user?.role === 'contributor') navigate('/submissions/new')
                }}
              >
                <i className="ti ti-photo-up"></i>
                <span id="side-nav-submit-lbl">{navVisibility(user).submitLabel}</span>
              </button>
              <button
                className="sidebar-link"
                id="side-nav-manage"
                style={{ display: navVisibility(user).manage ? 'flex' : 'none' }}
                onClick={() => {
                  if (user?.role === 'admin' || user?.role === 'validator') setOpenModal('invite')
                }}
              >
                <i className="ti ti-users"></i> Manage Users
              </button>
              <button
                className="sidebar-link"
                id="side-nav-schedule"
                style={{ display: navVisibility(user).schedule ? 'flex' : 'none' }}
              >
                <i className="ti ti-calendar-event"></i> Scheduler
              </button>
            </div>
          </aside>

          {/* MAIN CONTENT AREA */}
          <div className="dash-content-container">
            <nav className="dash-nav">
              <div className="dash-brand">
                <div className="dash-brand-icon">
                  <svg viewBox="0 0 24 24" style={{ width: 18, height: 18, fill: 'white' }}>
                    <path d="M12 2L22 7V17L12 22L2 17V7L12 2Z" />
                  </svg>
                </div>
                <div className="dash-brand-name">
                  DASIG<em>Connect</em>
                </div>
              </div>
              <div className="dash-nav-right">
                <div className={`role-chip ${roleChip(user).className}`} id="role-chip">
                  {roleChip(user).label}
                </div>
                <div className="dash-avatar" id="dash-avatar" onClick={onToggleDropdown}>
                  <span id="dash-initials">{user?.initials ?? 'NA'}</span>
                  <div className={`user-dropdown${showDropdown ? '' : ' hidden'}`} id="user-dropdown">
                    <div className="udrop-header">
                      <div className="udrop-name" id="dd-name">
                        {user?.name ?? 'Unknown'}
                      </div>
                      <div className="udrop-role" id="dd-role-inst">
                        {user
                          ? `${capitalize(user.role)} · ${shortInstitution(user.inst)}`
                          : ''}
                      </div>
                    </div>
                    <div className="udrop-item">
                      <i className="ti ti-key"></i> Change Password
                    </div>
                    <div className="udrop-item">
                      <i className="ti ti-settings"></i> Account Settings
                    </div>
                    <div className="udrop-sep"></div>
                    <div className="udrop-item danger" onClick={onLogout}>
                      <i className="ti ti-logout" style={{ color: 'var(--error)' }}></i> Sign Out
                    </div>
                  </div>
                </div>
              </div>
            </nav>

          <div className="dash-body">
            <div className="dash-page-header">
              <div className="dash-greeting" id="dash-greeting">
                {greeting(user)}
              </div>
              <div className="dash-subline" id="dash-subline">
                {subline(user)}
              </div>
            </div>

            <div className="first-login-notice" id="first-login-notice">
              <i className={notice(user).icon}></i>
              <div dangerouslySetInnerHTML={{ __html: notice(user).html }}></div>
            </div>

            <div className={`fb-bar${roleChip(user).className === 'chip-admin' ? '' : ' hidden'}`} id="fb-bar">
              <div className="fb-bar-left">
                <i className="ti ti-brand-facebook fb-icon"></i>
                <div className="fb-bar-text">
                  <div className="fb-bar-title">DASIG Facebook Page Connected</div>
                  <div className="fb-bar-sub">
                    Approved content can be scheduled directly to the DASIG Facebook page · Last synced 2 min ago
                  </div>
                </div>
              </div>
              <button type="button" className="fb-btn">
                <i className="ti ti-settings" style={{ marginRight: 5 }}></i>Manage Connection
              </button>
            </div>

            <div className="stat-grid" id="stat-grid">
              {statsForRole(user, dashboardStats, institutions.length).map((stat) => (
                <div className="stat-card" key={stat.label}>
                  <div className="stat-icon" style={{ color: stat.color }}>
                    <i className={stat.icon}></i>
                  </div>
                  <div className="stat-label">{stat.label}</div>
                  <div
                    className={`stat-value${stat.highlight ? ' highlight' : ''}`}
                    style={stat.valueStyle}
                  >
                    {stat.value}
                  </div>
                </div>
              ))}
            </div>

            <div className="section-title">
              <i className="ti ti-bolt"></i> Quick Actions
            </div>
            <div className="action-grid" id="action-grid">
              {actionsForRole(user).map((action) => {
                const isClickable =
                  (user?.role === 'admin' &&
                    (action.title === 'Invite Users' || action.title === 'Add Institution')) ||
                  (user?.role === 'validator' && action.title === 'Manage Contributors')
                const Element = isClickable ? 'button' : 'div'
                return (
                  <Element
                    key={action.title}
                    className={`action-card${isClickable ? ' action-card-clickable' : ''}`}
                    style={action.emphasized ? { border: '1.5px solid #BFDBFE' } : undefined}
                    onClick={
                      isClickable
                        ? () => handleActionClick(action.title)
                        : undefined
                    }
                    type={isClickable ? 'button' : undefined}
                  >
                    <div className={`action-card-icon ${action.accent}`}>
                      <i className={action.icon}></i>
                    </div>
                    <div className="action-card-text">
                      <div className="action-title">{action.title}</div>
                      <div className="action-sub">{action.subtitle}</div>
                    </div>
                  </Element>
                )
              })}
            </div>

            <div className="section-title">
              <i className="ti ti-history"></i> Recent Activity
            </div>
            <div className="card-wrap">
              <table className="data-table" id="activity-table">
                <thead>
                  <tr>
                    <th>Event / Post</th>
                    <th>Institution</th>
                    <th>Submitted</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody id="activity-body">
                  {activityForRole(user).length === 0 ? (
                    <tr>
                      <td colSpan={4} style={{ textAlign: 'center', padding: 28, color: 'var(--d-muted)', fontSize: 13 }}>
                        <i
                          className="ti ti-photo-off"
                          style={{ fontSize: 28, display: 'block', marginBottom: 8, opacity: 0.4 }}
                        ></i>
                        No submissions yet. Start by submitting your first event
                        content.
                      </td>
                    </tr>
                  ) : (
                    activityForRole(user).map((row) => (
                      <tr key={`${row.title}-${row.submitted}`}>
                        <td>
                          <strong>{row.title}</strong>
                          <br />
                          <span style={{ fontSize: 11, color: 'var(--d-muted)' }}>{row.subtitle}</span>
                        </td>
                        <td>{row.institution}</td>
                        <td>{row.submitted}</td>
                        <td>
                          <span className={`status-pill ${row.status.className}`}>
                            <i className={row.status.icon} style={{ fontSize: 11 }}></i> {row.status.label}
                          </span>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            {openModal === 'institution' && (
              <div className="dash-modal-backdrop" onClick={handleCloseModals}>
                <div
                  className="dash-modal-card"
                  onClick={(event) => event.stopPropagation()}
                >
                  <div className="dash-modal-header">
                    <div>
                      <div className="dash-modal-title">Add Institution</div>
                      <div className="dash-modal-sub">
                        Provision a new HEI workspace and bind its email domain.
                      </div>
                    </div>
                    <button
                      type="button"
                      className="dash-modal-close"
                      onClick={handleCloseModals}
                      aria-label="Close"
                    >
                      <i className="ti ti-x"></i>
                    </button>
                  </div>

                  <form onSubmit={handleProvisionInstitution}>
                    <div className="dash-field">
                      <label className="dash-field-label">Institution Name</label>
                      <input
                        className="dash-input"
                        placeholder="Silliman University"
                        value={instName}
                        onChange={(event) => setInstName(event.target.value)}
                      />
                    </div>

                    <div className="dash-field">
                      <label className="dash-field-label">Associated Email Domain</label>
                      <input
                        className="dash-input"
                        placeholder="su.edu.ph"
                        value={instDomain}
                        onChange={(event) => setInstDomain(event.target.value)}
                      />
                      <div className="dash-field-hint">
                        Used to auto-route contributors and apply institution branding.
                      </div>
                    </div>

                    <div className="dash-inline-field">
                      <div>
                        <div className="dash-inline-label">Generated Institution Code</div>
                        <div className="dash-inline-sub">Based on name/domain. You can edit later.</div>
                      </div>
                      <div className="dash-pill">
                        {generateInstitutionCode(instName, normalizeDomain(instDomain)) || 'AUTO'}
                      </div>
                    </div>

                    {instError && (
                      <div className="alert alert-err">
                        <i className="ti ti-alert-circle"></i>
                        <div>{instError}</div>
                      </div>
                    )}

                    {instSuccess && (
                      <div className="alert alert-ok">
                        <i className="ti ti-circle-check"></i>
                        <div>
                          {instSuccess.name} was provisioned. Code: {instSuccess.code}
                        </div>
                      </div>
                    )}

                    <div className="dash-modal-actions">
                      <button type="button" className="btn-ghost" onClick={handleCloseModals}>
                        Cancel
                      </button>
                      <button type="submit" className="btn-primary" disabled={instLoading}>
                        {instLoading ? 'Provisioning...' : 'Add Institution'}
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            )}

            {openModal === 'invite' && (
              <div className="dash-modal-backdrop" onClick={handleCloseModals}>
                <div
                  className="dash-modal-card"
                  onClick={(event) => event.stopPropagation()}
                >
                  <div className="dash-modal-header">
                    <div>
                      <div className="dash-modal-title">Invite Users</div>
                      <div className="dash-modal-sub">
                        Send secure onboarding links to Validators or Contributors.
                      </div>
                    </div>
                    <button
                      type="button"
                      className="dash-modal-close"
                      onClick={handleCloseModals}
                      aria-label="Close"
                    >
                      <i className="ti ti-x"></i>
                    </button>
                  </div>

                  {institutionsError && (
                    <div className="alert alert-err">
                      <i className="ti ti-alert-circle"></i>
                      <div>{institutionsError}</div>
                    </div>
                  )}

                  <form onSubmit={handleSendInvitations}>
                    <div className="dash-field">
                      <label className="dash-field-label">Recipient Emails</label>
                      <textarea
                        className="dash-textarea"
                        placeholder="dean@su.edu.ph, registrar@su.edu.ph"
                        value={inviteEmailsText}
                        onChange={(event) => setInviteEmailsText(event.target.value)}
                      ></textarea>
                      <div className="dash-field-hint">
                        Enter up to 15 emails separated by commas or new lines.
                      </div>
                    </div>

                    <div className="dash-field-grid">
                      <div className="dash-field">
                        <label className="dash-field-label">Role</label>
                        <select
                          className="dash-select"
                          value={inviteRole}
                          onChange={(event) => setInviteRole(event.target.value as 'validator' | 'contributor')}
                          disabled={user?.role === 'validator'}
                        >
                          <option value="validator">Validator</option>
                          <option value="contributor">Contributor</option>
                        </select>
                      </div>
                      <div className="dash-field">
                        <label className="dash-field-label">Institution</label>
                        <select
                          className="dash-select"
                          value={inviteSelectedInstId}
                          onChange={(event) => setInviteSelectedInstId(event.target.value)}
                          disabled={institutionsLoading || institutions.length === 0}
                        >
                          {institutionsLoading && <option>Loading institutions...</option>}
                          {!institutionsLoading && institutions.length === 0 && (
                            <option>No institutions yet</option>
                          )}
                          {!institutionsLoading &&
                            institutions.map((inst) => (
                              <option key={inst.id} value={inst.id}>
                                {inst.name}
                              </option>
                            ))}
                        </select>
                      </div>
                    </div>

                    {inviteResults && (
                      <div className="dash-invite-results">
                        <div className="dash-invite-summary">
                          {inviteResults.success.length} of {inviteResults.total} invites sent.
                        </div>
                        {inviteResults.success.length > 0 && (
                          <div className="dash-invite-list">
                            <strong>Sent</strong>
                            <div>{inviteResults.success.join(', ')}</div>
                          </div>
                        )}
                        {inviteResults.failed.length > 0 && (
                          <div className="dash-invite-list dash-invite-failed">
                            <strong>Failed</strong>
                            {inviteResults.failed.map((item) => (
                              <div key={item.email}>
                                {item.email}: {item.reason}
                              </div>
                            ))}
                            <button
                              type="button"
                              className="btn-text"
                              onClick={handleResubmitFailedOnly}
                            >
                              Resubmit failed only
                            </button>
                          </div>
                        )}
                      </div>
                    )}

                    <div className="dash-modal-actions">
                      <button type="button" className="btn-ghost" onClick={handleCloseModals}>
                        Cancel
                      </button>
                      <button
                        type="submit"
                        className="btn-primary"
                        disabled={inviteLoading || institutions.length === 0}
                      >
                        {inviteLoading ? 'Sending...' : 'Send Invite'}
                      </button>
                    </div>
                  </form>
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
    </Screen>
  )
}

const DOMAIN_MAP: Record<string, string> = {
  'citu': 'CIT-U',
  'su': 'Silliman University',
  'silliman': 'Silliman University',
  'usc': 'University of San Carlos',
  'vsu': 'Visayas State University',
  'uc': 'University of Cebu',
  'dasigconnect': 'DASIG Connect',
}

function getInstitutionName(user: User | null): string {
  if (!user) return 'Institution'
  if (user.role === 'admin') return 'DASIG'
  
  const emailDomain = user.email.split('@')[1]?.split('.')[0]?.toLowerCase() || ''
  return DOMAIN_MAP[emailDomain] || emailDomain.toUpperCase() || 'Institution'
}

function navVisibility(user: User | null) {
  if (!user) {
    return { submit: false, manage: false, schedule: false, submitLabel: 'Submit Content' }
  }
  if (user.role === 'admin') {
    return {
      submit: false,
      manage: true,
      schedule: true,
      submitLabel: 'Submissions',
    }
  }
  if (user.role === 'validator') {
    return {
      submit: true,
      manage: true,
      schedule: true,
      submitLabel: 'Review Queue',
    }
  }
  return {
    submit: true,
    manage: false,
    schedule: false,
    submitLabel: 'Submit Content',
  }
}

function roleChip(user: User | null) {
  if (!user) {
    return { className: 'chip-contributor', label: 'Contributor' }
  }
  if (user.role === 'admin') {
    return { className: 'chip-admin', label: 'Administrator' }
  }
  if (user.role === 'validator') {
    return { className: 'chip-validator', label: 'Validator' }
  }
  return { className: 'chip-contributor', label: 'Contributor' }
}

function greeting(user: User | null) {
  const hour = new Date().getHours()
  const label = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening'
  const name = user?.name?.split(' ')[0] ?? 'there'
  return `${label}, ${name}.`
}

function subline(user: User | null) {
  if (!user) return ''
  const instName = getInstitutionName(user)
  return `${capitalize(user.role)} · ${instName} · First login today`
}

function notice(user: User | null) {
  if (!user) {
    return {
      icon: 'ti ti-confetti',
      html: '<strong>Welcome to DASIGConnect!</strong> Your account is now active and bound to your institution\'s workspace. This is your first login — explore your dashboard and start submitting content for your institution\'s events.',
    }
  }
  if (user.role === 'admin') {
    return {
      icon: 'ti ti-alert-circle',
      html: '<strong>Administrator workspace.</strong> You have full network-wide visibility. <strong>0 failed batch invitations</strong> require attention. <strong>0 submissions</strong> are pending scheduler assignment.',
    }
  }
  if (user.role === 'validator') {
    const instName = getInstitutionName(user)
    return {
      icon: 'ti ti-clipboard-check',
      html: `Welcome back, <strong>Validator.</strong> You have <strong>0 submissions pending your review</strong> from ${instName} contributors. Approved content moves to the DASIG Administrator for scheduling.`,
    }
  }
  const instName = getInstitutionName(user)
  return {
    icon: 'ti ti-confetti',
    html: `<strong>Welcome to DASIGConnect!</strong> Your account is active and bound to ${instName}'s workspace. Submit photos and videos from your institution's events — your Validator will review them before they go to the DASIG Facebook page.`,
  }
}

function statsForRole(user: User | null, stats: DashboardStats, institutionCount: number): StatItem[] {
  if (!user) return []
  const submissions = stats.submissions
  const publishedCount = submissions.filter((item) =>
    item.status === 'published' || item.status === 'published_manual' || item.status === 'admin_direct_post'
  ).length
  const scheduledCount = submissions.filter((item) => item.status === 'scheduled').length
  const reviewCount = submissions.filter((item) => item.status === 'pending' || item.status === 'in_review').length
  if (user.role === 'admin') {
    return [
      { icon: 'ti ti-building', color: '#1877F2', label: 'Member Institutions', value: String(institutionCount) },
      { icon: 'ti ti-users', color: '#16A34A', label: 'Total Users', value: String(stats.contributors + stats.validators) },
      { icon: 'ti ti-clock-pause', color: '#D97706', label: 'Pending Invites', value: '0', highlight: true },
      { icon: 'ti ti-calendar-event', color: '#7C3AED', label: 'Scheduled Posts', value: String(scheduledCount) },
      { icon: 'ti ti-photo-check', color: '#1877F2', label: 'Published This Month', value: String(publishedCount) },
    ]
  }
  if (user.role === 'validator') {
    return [
      { icon: 'ti ti-file-time', color: '#D97706', label: 'Pending Review', value: String(reviewCount), highlight: true },
      { icon: 'ti ti-circle-check', color: '#16A34A', label: 'Approved This Month', value: String(scheduledCount + publishedCount) },
      { icon: 'ti ti-users', color: '#1877F2', label: 'Contributors', value: String(stats.contributors) },
      {
        icon: 'ti ti-building',
        color: '#7C3AED',
        label: 'Institution',
        value: getInstitutionName(user),
        valueStyle: { fontSize: 16, paddingTop: 6 },
      },
    ]
  }
  return [
    { icon: 'ti ti-photo-up', color: '#1877F2', label: 'My Submissions', value: String(submissions.length) },
    { icon: 'ti ti-circle-check', color: '#16A34A', label: 'Approved', value: String(scheduledCount + publishedCount) },
    { icon: 'ti ti-clock', color: '#D97706', label: 'Under Review', value: String(reviewCount) },
    { icon: 'ti ti-brand-facebook', color: '#7C3AED', label: 'Published', value: String(publishedCount) },
  ]
}

function actionsForRole(user: User | null): ActionItem[] {
  if (!user) return []
  if (user.role === 'admin') {
    return [
      {
        icon: 'ti ti-user-plus',
        accent: 'ac-blue',
        title: 'Invite Users',
        subtitle: 'Batch invite Contributors by institution',
      },
      {
        icon: 'ti ti-building-plus',
        accent: 'ac-green',
        title: 'Add Institution',
        subtitle: 'Provision a new HEI workspace',
      },
      {
        icon: 'ti ti-calendar-plus',
        accent: 'ac-gold',
        title: 'Schedule Post',
        subtitle: 'Assign approved content to Facebook queue',
      },
      {
        icon: 'ti ti-chart-bar',
        accent: 'ac-purple',
        title: 'View Analytics',
        subtitle: 'Facebook reach & engagement overview',
      },
    ]
  }
  if (user.role === 'validator') {
    return [
      {
        icon: 'ti ti-clipboard-list',
        accent: 'ac-gold',
        title: 'Review Queue',
        subtitle: '0 submissions awaiting your approval',
      },
      {
        icon: 'ti ti-user-check',
        accent: 'ac-blue',
        title: 'Manage Contributors',
        subtitle: 'View and manage institution members',
      },
      {
        icon: 'ti ti-history',
        accent: 'ac-green',
        title: 'Approval History',
        subtitle: 'View all past approvals and rejections',
      },
    ]
  }
  return [
    {
      icon: 'ti ti-photo-up',
      accent: 'ac-blue',
      title: 'Submit Event Content',
      subtitle: 'Upload photos, videos & captions',
      emphasized: true,
    },
    {
      icon: 'ti ti-history',
      accent: 'ac-green',
      title: 'My Submissions',
      subtitle: 'Track status of your submissions',
    },
    {
      icon: 'ti ti-bulb',
      accent: 'ac-gold',
      title: 'Content Guidelines',
      subtitle: 'What to submit and how to tag it',
    },
  ]
}

function activityForRole(user: User | null): ActivityItem[] {
  if (!user) return emptyContributorActivity
  return emptyContributorActivity
}

function capitalize(value: string) {
  return value.charAt(0).toUpperCase() + value.slice(1)
}

function shortInstitution(value: string) {
  return value
}

function normalizeDomain(domain: string) {
  return domain.trim().toLowerCase().replace(/^@/, '')
}

function isValidDomain(domain: string) {
  return /^[a-z0-9.-]+\.[a-z]{2,}$/.test(domain)
}

function isValidEmail(email: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

function getApiErrorMessage(err: any, fallback: string) {
  return err.response?.data?.error || err.response?.data?.message || err.message || fallback
}

function generateInstitutionCode(name: string, domain: string) {
  const domainPrefix = domain.split('.')[0] || ''
  if (domainPrefix.length > 1) {
    return domainPrefix.toUpperCase()
  }
  const parts = name
    .replace(/[^A-Za-z0-9 ]/g, ' ')
    .split(' ')
    .filter(Boolean)
  const initials = parts.map((part) => part.charAt(0)).join('')
  return initials.toUpperCase() || 'INST'
}
