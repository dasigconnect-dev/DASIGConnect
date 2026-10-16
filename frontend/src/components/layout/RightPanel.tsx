import type { ReactNode } from 'react'
import ShapeGrid from '../ui/ShapeGrid'

interface RightPanelProps {
  children: ReactNode
}

export default function RightPanel({ children }: RightPanelProps) {
  return (
    <div className="panel-r">
      <div className="shapegrid-bg-wrap auth-grid" aria-hidden="true">
        <ShapeGrid
          speed={0.35}
          squareSize={42}
          direction="diagonal"
          borderColor="rgba(24, 119, 242, 0.08)"
          hoverFillColor="rgba(24, 119, 242, 0.16)"
          shape="square"
          hoverTrailAmount={5}
        />
      </div>
      <div className="form-card">{children}</div>
    </div>
  )
}
