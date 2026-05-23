interface ConfirmDialogProps {
  title: string
  message: string
  confirmLabel?: string
  dangerous?: boolean
  onConfirm: () => void
  onCancel: () => void
}

export default function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Confirm',
  dangerous = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  return (
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
        <div className="um-confirm-actions">
          <button type="button" className="um-confirm-cancel" onClick={onCancel}>
            Cancel
          </button>
          <button
            type="button"
            className={`um-confirm-ok${dangerous ? ' is-danger' : ''}`}
            onClick={onConfirm}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
