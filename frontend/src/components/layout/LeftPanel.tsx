import type { ReactNode } from 'react'

interface LeftPanelProps {
  children: ReactNode
}

export default function LeftPanel({ children }: LeftPanelProps) {
  return (
    <div className="panel-l">
      <div className="l-orb"></div>
      <div className="l-content">{children}</div>
    </div>
  )
}
