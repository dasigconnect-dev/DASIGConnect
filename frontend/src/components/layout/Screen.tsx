import type { ReactNode } from 'react'

interface ScreenProps {
  id: string
  active: boolean
  children: ReactNode
}

export default function Screen({ id, active, children }: ScreenProps) {
  return (
    <div id={`screen-${id}`} className={`screen${active ? ' active' : ''}`}>
      {children}
    </div>
  )
}
