import { Outlet, useLocation } from 'react-router-dom'
import Screen from './Screen'
import LeftPanel from './LeftPanel'
import RightPanel from './RightPanel'
import AuthShowcase from '../../features/auth/components/AuthShowcase'
import PageTransition from '../common/PageTransition'

export default function AuthLayout() {
  const { pathname } = useLocation()

  // Retain appropriate screen ID for route-specific CSS styling (e.g. #screen-invite)
  const getScreenId = (path: string) => {
    if (path.startsWith('/invite')) return 'invite'
    if (path.startsWith('/forgot-password-sent')) return 'forgot-sent'
    if (path.startsWith('/forgot-password')) return 'forgot'
    if (path.startsWith('/reset-password')) return 'reset-password'
    if (path.startsWith('/no-account')) return 'no-account'
    return 'login'
  }

  return (
    <Screen id={getScreenId(pathname)} active={true}>
      <div className="split">
        <LeftPanel>
          <AuthShowcase />
        </LeftPanel>
        <RightPanel>
          <PageTransition>
            <Outlet />
          </PageTransition>
        </RightPanel>
      </div>
    </Screen>
  )
}
