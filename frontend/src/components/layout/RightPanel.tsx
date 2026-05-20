import type { ReactNode } from 'react'

interface RightPanelProps {
  children: ReactNode
}

export default function RightPanel({ children }: RightPanelProps) {
  return (
    <div className="panel-r">
      <div className="form-card">{children}</div>
    </div>
  )
}
