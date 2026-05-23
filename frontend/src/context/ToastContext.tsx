import {
  createContext,
  useCallback,
  useContext,
  useRef,
  useState,
  type ReactNode,
} from 'react'

export type ToastType = 'success' | 'error' | 'info' | 'warning'

export interface ToastItem {
  id: string
  message: string
  type: ToastType
  duration: number
}

interface ToastContextValue {
  toasts: ToastItem[]
  add: (message: string, type: ToastType, duration?: number) => void
  dismiss: (id: string) => void
}

const ToastContext = createContext<ToastContextValue | null>(null)

const DEFAULT_DURATION: Record<ToastType, number> = {
  success: 4500,
  info: 4500,
  warning: 5000,
  error: 5000,
}

let _nextId = 0
function nextId() {
  _nextId += 1
  return String(_nextId)
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  // Track dismiss timers so we can clear them if manually dismissed
  const timers = useRef<Map<string, number>>(new Map())

  const dismiss = useCallback((id: string) => {
    const timerId = timers.current.get(id)
    if (timerId !== undefined) {
      window.clearTimeout(timerId)
      timers.current.delete(id)
    }
    setToasts((prev) => prev.filter((t) => t.id !== id))
  }, [])

  const add = useCallback(
    (message: string, type: ToastType, duration?: number) => {
      const id = nextId()
      const resolvedDuration = duration ?? DEFAULT_DURATION[type]
      setToasts((prev) => [...prev, { id, message, type, duration: resolvedDuration }])
      const timerId = window.setTimeout(() => dismiss(id), resolvedDuration + 280) // +280 for exit anim
      timers.current.set(id, timerId)
    },
    [dismiss],
  )

  return (
    <ToastContext.Provider value={{ toasts, add, dismiss }}>
      {children}
    </ToastContext.Provider>
  )
}

function useToastContext(): ToastContextValue {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast must be used inside ToastProvider')
  return ctx
}

export interface ToastControls {
  success: (message: string, duration?: number) => void
  error: (message: string, duration?: number) => void
  info: (message: string, duration?: number) => void
  warning: (message: string, duration?: number) => void
  dismiss: (id: string) => void
}

export function useToast(): ToastControls {
  const { add, dismiss } = useToastContext()
  return {
    success: (msg, duration) => add(msg, 'success', duration),
    error:   (msg, duration) => add(msg, 'error',   duration),
    info:    (msg, duration) => add(msg, 'info',     duration),
    warning: (msg, duration) => add(msg, 'warning',  duration),
    dismiss,
  }
}

export function useToastItems(): ToastItem[] {
  return useToastContext().toasts
}

export function useToastDismiss(): (id: string) => void {
  return useToastContext().dismiss
}
