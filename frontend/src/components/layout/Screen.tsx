import type { ReactNode } from 'react'
import '../../styles/auth-layout.css'

interface ScreenProps {
  id: string
  active: boolean
  children: ReactNode
}

export default function Screen({ id, active, children }: ScreenProps) {
  return (
    <main id={`screen-${id}`} className={`screen${active ? ' active' : ''}`}>
      {children}
    </main>
  )
}
