import { useId, useState } from 'react'
import { createPortal } from 'react-dom'

interface ConfirmDialogProps {
  title: string
  message: string
  confirmLabel?: string
  dangerous?: boolean
  /**
   * When set, the confirm button stays disabled until the user types this exact
   * string (trimmed, case-insensitive). Used to gate destructive actions like
   * permanently removing an account.
   */
  requireTypedConfirmation?: string
  onConfirm: () => void
  onCancel: () => void
}

export default function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Confirm',
  dangerous = false,
  requireTypedConfirmation,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const [typed, setTyped] = useState('')
  const inputId = useId()

  if (typeof document === 'undefined') return null

  const typedOk =
    requireTypedConfirmation == null ||
    typed.trim().toLowerCase() === requireTypedConfirmation.trim().toLowerCase()

  return createPortal(
    <div
      className="um-confirm-backdrop"
      role="dialog"
      aria-modal="true"
      aria-labelledby="um-confirm-title"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onCancel()
      }}
    >
      <div className="um-confirm-card">
        <div className="um-confirm-header">
          <span id="um-confirm-title" className="um-confirm-title">{title}</span>
        </div>
        <p className="um-confirm-message">{message}</p>

        {requireTypedConfirmation != null && (
          <div className="um-confirm-typed">
            <label htmlFor={inputId} className="um-confirm-typed-label">
              Type <strong>{requireTypedConfirmation}</strong> to confirm
            </label>
            <input
              id={inputId}
              type="text"
              className="um-confirm-typed-input"
              value={typed}
              onChange={(event) => setTyped(event.target.value)}
              autoComplete="off"
              autoCorrect="off"
              autoCapitalize="off"
              spellCheck={false}
              autoFocus
            />
          </div>
        )}

        <div className="um-confirm-actions">
          <button type="button" className="um-confirm-cancel" onClick={onCancel}>
            Cancel
          </button>
          <button
            type="button"
            className={`um-confirm-ok${dangerous ? ' is-danger' : ''}`}
            onClick={onConfirm}
            disabled={!typedOk}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
