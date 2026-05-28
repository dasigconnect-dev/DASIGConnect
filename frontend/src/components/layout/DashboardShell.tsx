import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import type { User } from '../../types/auth.types'
import Spinner from '../common/Spinner'

export type DashboardNavId = 'home' | 'submit' | 'institution-management' | 'user-management' | 'scheduler' | 'resolution' | 'analytics' | 'media-repository' | 'notifications'

interface DashboardShellProps {
  user: User
  activeNav: DashboardNavId
  showBanner: boolean
  bannerTime: string
  showDropdown: boolean
  onToggleDropdown: () => void
  onDismissBanner: () => void
  onStayLoggedIn: () => void
  onLogout: () => void
  logoutLoading: boolean
  notificationBadge?: number
  children: ReactNode
}

interface DashboardNavItem {
  id: DashboardNavId
  icon: string
  label: string
  path?: string
  visible: boolean
}

export default function DashboardShell({
  user,
  activeNav,
  showBanner,
  bannerTime,
  showDropdown,
  onToggleDropdown,
  onDismissBanner,
  onStayLoggedIn,
  onLogout,
  logoutLoading,
  notificationBadge = 0,
  children,
}: DashboardShellProps) {
  const navigate = useNavigate()
  const navItems = dashboardNavItems(user)

  return (
    <>
      <div id="session-banner" className={showBanner ? '' : 'hidden'}>
        <div className="banner-msg">
          <i className="ti ti-clock-exclamation"></i>
          <span>
            Your session expires in <strong id="banner-time">{bannerTime}</strong>. Stay logged in?
          </span>
        </div>
        <div className="banner-actions">
          <button type="button" className="banner-btn banner-btn-stay" onClick={onStayLoggedIn}>
            Stay Logged In
          </button>
          <button type="button" className="banner-btn banner-btn-dismiss" onClick={onDismissBanner}>
            Dismiss
          </button>
        </div>
      </div>

      <div className="dash-shell">
        <aside className="dash-sidebar" id="dash-sidebar">
          <div className="sidebar-brand-wrapper">
            <div className="dash-brand">
              <div className="dash-brand-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 2L22 7V17L12 22L2 17V7L12 2Z" />
                </svg>
              </div>
              <div className="dash-brand-name" style={{ marginLeft: 8 }}>
                DASIG<em>Connect</em>
              </div>
            </div>
          </div>

          <div className="sidebar-nav">
            {groupDashboardNavItems(navItems.filter((item) => item.visible)).map((group) => (
              <div className="sidebar-nav-group" key={group.label}>
                <div className="sidebar-nav-label">{group.label}</div>
                {group.items.map((item) => (
              <button
                className={`sidebar-link${activeNav === item.id ? ' active' : ''}`}
                type="button"
                key={item.id}
                onClick={() => {
                  if (item.path) navigate(item.path)
                }}
                aria-current={activeNav === item.id ? 'page' : undefined}
                aria-disabled={!item.path}
              >
                <i className={item.icon}></i>
                <span>{item.label}</span>
                {item.id === 'notifications' && notificationBadge > 0 && (
                  <span className="sidebar-notif-badge" aria-label={`${notificationBadge} unread notifications`}>
                    {notificationBadge > 99 ? '99+' : notificationBadge}
                    <span className="sidebar-notif-badge-label">new</span>
                  </span>
                )}
              </button>
                ))}
              </div>
            ))}
          </div>
        </aside>

        <div className="dash-content-container">
          <nav className="dash-nav">
            <div className="dash-brand">
              <div className="dash-brand-icon">
                <svg viewBox="0 0 24 24" aria-hidden="true">
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
              <div
                className={`dash-avatar${showDropdown ? ' open' : ''}`}
                id="dash-avatar"
                onClick={onToggleDropdown}
                role="button"
                tabIndex={0}
                aria-haspopup="menu"
                aria-expanded={showDropdown}
                onKeyDown={(event) => {
                  if (event.key === 'Enter' || event.key === ' ') {
                    event.preventDefault()
                    onToggleDropdown()
                  }
                }}
              >
                <span id="dash-initials">{user.initials}</span>
                <i className="ti ti-chevron-down dash-avatar-caret" aria-hidden="true"></i>
                <div className={`user-dropdown${showDropdown ? '' : ' hidden'}`} id="user-dropdown">
                  <div className="udrop-header">
                    <div className="udrop-name" id="dd-name">
                      {user.name}
                    </div>
                    <div className="udrop-role" id="dd-role-inst">
                      {capitalize(user.role)} · {getInstitutionName(user)}
                    </div>
                  </div>
                  <div className="udrop-item">
                    <i className="ti ti-key"></i> Change Password
                  </div>
                  <div className="udrop-item">
                    <i className="ti ti-settings"></i> Account Settings
                  </div>
                  <div className="udrop-sep"></div>
                  <button
                    type="button"
                    className="udrop-item danger udrop-button"
                    onClick={onLogout}
                    disabled={logoutLoading}
                    aria-busy={logoutLoading}
                  >
                    {logoutLoading ? (
                      <Spinner size="xs" color="inherit" aria-label="Signing out" />
                    ) : (
                      <i className="ti ti-logout" style={{ color: 'var(--error)' }}></i>
                    )}
                    <span>{logoutLoading ? 'Signing out' : 'Sign Out'}</span>
                  </button>
                </div>
              </div>
            </div>
          </nav>

          {children}
        </div>
      </div>
    </>
  )
}

function dashboardNavItems(user: User): DashboardNavItem[] {
  return [
    {
      id: 'home',
      icon: 'ti ti-layout-dashboard',
      label: 'Home',
      path: '/dashboard',
      visible: true,
    },
    {
      id: 'submit',
      icon: user.role === 'validator' ? 'ti ti-clipboard-list' : 'ti ti-photo-up',
      label: user.role === 'validator' ? 'Review Queue' : 'Submit Content',
      path: user.role === 'validator' ? '/validation/queue' : '/submissions/new',
      visible: user.role === 'validator' || user.role === 'contributor',
    },
    {
      id: 'institution-management',
      icon: 'ti ti-building',
      label: 'Institution Management',
      path: '/admin/institution-management',
      visible: user.role === 'admin',
    },
    {
      id: 'user-management',
      icon: 'ti ti-users',
      label: 'User Management',
      path: '/admin/user-management/invitations',
      visible: user.role === 'validator',
    },
    {
      id: 'media-repository',
      icon: 'ti ti-photo',
      label: 'Media Repository',
      path: '/media-repository',
      visible: true,
    },
    {
      id: 'notifications',
      icon: 'ti ti-bell',
      label: 'Notifications',
      path: '/notifications',
      visible: true,
    },
    {
      id: 'scheduler',
      icon: 'ti ti-calendar-event',
      label: 'Calendar',
      path: '/scheduler/calendar',
      visible: true,
    },
    {
      id: 'resolution',
      icon: 'ti ti-alert-triangle',
      label: 'Resolution Center',
      path: '/admin/resolution',
      visible: user.role === 'admin',
    },
    {
      id: 'analytics',
      icon: 'ti ti-chart-bar',
      label: 'Analytics',
      path: '/analytics',
      visible: true,
    },
  ]
}

function groupDashboardNavItems(items: DashboardNavItem[]) {
  return [
    {
      label: 'Workspace',
      items: items.filter((item) => ['home', 'submit', 'media-repository', 'notifications'].includes(item.id)),
    },
    {
      label: 'Operations',
      items: items.filter((item) => ['institution-management', 'user-management', 'scheduler', 'resolution', 'analytics'].includes(item.id)),
    },
  ].filter((group) => group.items.length > 0)
}

function roleChip(user: User) {
  if (user.role === 'admin') return { className: 'chip-admin', label: 'Administrator' }
  if (user.role === 'validator') return { className: 'chip-validator', label: 'Validator' }
  return { className: 'chip-contributor', label: 'Contributor' }
}

function getInstitutionName(user: User) {
  return user.inst?.trim() || 'Institution'
}

function capitalize(value: string) {
  return value.charAt(0).toUpperCase() + value.slice(1)
}
