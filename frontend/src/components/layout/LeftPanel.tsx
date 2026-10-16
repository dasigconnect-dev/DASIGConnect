import type { ReactNode } from 'react'
import TopologyField from '../ui/topology-field'

interface LeftPanelProps {
  children: ReactNode
}

export default function LeftPanel({ children }: LeftPanelProps) {
  return (
    <div className="panel-l">
      <TopologyField className="panel-l-topology" />
      <div className="l-content">{children}</div>
    </div>
  )
}
