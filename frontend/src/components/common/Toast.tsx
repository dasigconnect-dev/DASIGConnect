import { useEffect, useState } from 'react'
import { useToastDismiss, useToastItems, type ToastItem, type ToastType } from '../../context/ToastContext'

const ICON: Record<ToastType, string> = {
  success: 'ti ti-circle-check',
  error:   'ti ti-alert-circle',
  info:    'ti ti-info-circle',
  warning: 'ti ti-alert-triangle',
}

function ToastEntry({ item }: { item: ToastItem }) {
  const dismiss = useToastDismiss()
  const [exiting, setExiting] = useState(false)

  useEffect(() => {
    const exitTimer = window.setTimeout(() => setExiting(true), item.duration)
    return () => window.clearTimeout(exitTimer)
  }, [item.duration])

  // After exit animation completes, remove from context
  useEffect(() => {
    if (!exiting) return
    const removeTimer = window.setTimeout(() => dismiss(item.id), 280)
    return () => window.clearTimeout(removeTimer)
  }, [exiting, item.id, dismiss])

  return (
    <div
      className={`dc-toast-item ${item.type}${exiting ? ' exiting' : ''}`}
      role={item.type === 'error' || item.type === 'warning' ? 'alert' : 'status'}
      aria-live={item.type === 'error' || item.type === 'warning' ? 'assertive' : 'polite'}
      aria-atomic="true"
    >
      <i className={`${ICON[item.type]} dc-toast-icon`} aria-hidden="true" />
      <span className="dc-toast-message">{item.message}</span>
      <button
        type="button"
        className="dc-toast-close"
        onClick={() => {
          setExiting(true)
          window.setTimeout(() => dismiss(item.id), 280)
        }}
        aria-label="Dismiss notification"
      >
        <i className="ti ti-x" aria-hidden="true" />
      </button>
    </div>
  )
}

export default function Toast() {
  const toasts = useToastItems()
  if (toasts.length === 0) return null
  return (
    <div className="dc-toast-root" aria-label="Notifications">
      {toasts.map((item) => (
        <ToastEntry key={item.id} item={item} />
      ))}
    </div>
  )
}
