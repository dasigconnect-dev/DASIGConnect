import { Outlet, useLocation } from 'react-router-dom'
import DashboardShell, { type DashboardNavId } from './DashboardShell'
import PageTransition from '../common/PageTransition'
import type { User } from '../../types/auth.types'

interface DashboardLayoutProps {
  user: User
  showBanner: boolean
  bannerTime: string
  showDropdown: boolean
  onToggleDropdown: () => void
  onDismissBanner: () => void
  onStayLoggedIn: () => void
  onLogout: () => void
  logoutLoading: boolean
}

function getActiveNav(pathname: string): DashboardNavId {
  if (pathname.startsWith('/admin/institution-management')) return 'institution-management'
  if (pathname.startsWith('/admin/user-management')) return 'user-management'
  if (pathname.startsWith('/admin/resolution')) return 'resolution'
  if (pathname.startsWith('/validation')) return 'submit'
  if (pathname.startsWith('/submissions')) return 'submit'
  if (pathname.startsWith('/scheduler')) return 'scheduler'
  if (pathname.startsWith('/analytics')) return 'analytics'
  return 'home'
}

export default function DashboardLayout({
  user,
  showBanner,
  bannerTime,
  showDropdown,
  onToggleDropdown,
  onDismissBanner,
  onStayLoggedIn,
  onLogout,
  logoutLoading,
}: DashboardLayoutProps) {
  const { pathname } = useLocation()
  return (
    <DashboardShell
      user={user}
      activeNav={getActiveNav(pathname)}
      showBanner={showBanner}
      bannerTime={bannerTime}
      showDropdown={showDropdown}
      onToggleDropdown={onToggleDropdown}
      onDismissBanner={onDismissBanner}
      onStayLoggedIn={onStayLoggedIn}
      onLogout={onLogout}
      logoutLoading={logoutLoading}
    >
      <PageTransition>
        <Outlet />
      </PageTransition>
    </DashboardShell>
  )
}
