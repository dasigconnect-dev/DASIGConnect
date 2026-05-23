import { useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'

interface PageTransitionProps {
  children: ReactNode
}

/**
 * Wraps route content and re-triggers the enter animation whenever
 * the pathname changes. Use inside a layout that renders <Outlet />.
 */
export default function PageTransition({ children }: PageTransitionProps) {
  const { pathname } = useLocation()
  return (
    <div key={pathname} className="dc-page-transition">
      {children}
    </div>
  )
}
