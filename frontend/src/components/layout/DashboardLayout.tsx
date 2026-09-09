import { useEffect } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { Outlet, useLocation } from 'react-router-dom'
import DashboardShell, { type DashboardNavId } from './DashboardShell'
import PageTransition from '../common/PageTransition'
import type { User } from '../../types/auth.types'
import { useNotificationUnreadCount } from '../../features/notifications/hooks/useNotifications'

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
  if (pathname.startsWith('/admin/admin-management')) return 'admin-management'
  if (pathname.startsWith('/admin/moderator-management')) return 'admin-management'
  if (pathname.startsWith('/admin/administrator-management')) return 'admin-management'
  if (pathname.startsWith('/admin/user-management')) return 'user-management'
  if (pathname.startsWith('/admin/system-health')) return 'system-health'
  if (pathname.startsWith('/admin/audit-log')) return 'audit-log'
  if (pathname.startsWith('/media-repository')) return 'media-repository'
  if (pathname.startsWith('/notifications')) return 'notifications'
  if (pathname.startsWith('/validation')) return 'review-queue'
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
  const queryClient = useQueryClient()
  const unreadCountQuery = useNotificationUnreadCount(user)
  const notificationBadge = unreadCountQuery.data ?? 0

  useEffect(() => {
    const refreshCount = () => {
      if (document.visibilityState === 'visible') {
        void queryClient.invalidateQueries({ queryKey: ['notifications'] })
      }
    }
    // Background poll; the SSE stream delivers new notifications in real time,
    // so this only needs to catch reads made on another device.
    const intervalId = window.setInterval(refreshCount, 3 * 60_000)
    const onFocus = () => refreshCount()
    window.addEventListener('focus', onFocus)
    return () => {
      window.clearInterval(intervalId)
      window.removeEventListener('focus', onFocus)
    }
  }, [queryClient])

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
      notificationBadge={notificationBadge}
    >
      <PageTransition>
        <Outlet />
      </PageTransition>
    </DashboardShell>
  )
}
