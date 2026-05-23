import { useRef, type KeyboardEvent } from 'react'

interface EmailChipsInputProps {
  chips: string[]
  draft: string
  onDraftChange: (value: string) => void
  onAdd: (email: string) => void
  onRemove: (index: number) => void
  disabled?: boolean
}

export default function EmailChipsInput({
  chips,
  draft,
  onDraftChange,
  onAdd,
  onRemove,
  disabled,
}: EmailChipsInputProps) {
  const inputRef = useRef<HTMLInputElement>(null)

  function commitDraft() {
    const trimmed = draft.trim().replace(/,+$/, '')
    if (trimmed) {
      onAdd(trimmed)
      onDraftChange('')
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      commitDraft()
      return
    }
    if (event.key === 'Backspace' && draft === '' && chips.length > 0) {
      onRemove(chips.length - 1)
    }
  }

  function handleChange(value: string) {
    if (value.endsWith(',') || value.endsWith(' ')) {
      const candidate = value.replace(/[, ]+$/, '').trim()
      if (candidate) onAdd(candidate)
      onDraftChange('')
    } else {
      onDraftChange(value)
    }
  }

  function handlePaste(event: React.ClipboardEvent<HTMLInputElement>) {
    const pasted = event.clipboardData.getData('text')
    const emails = pasted
      .split(/[\s,;]+/)
      .map((e) => e.trim())
      .filter((e) => e.length > 0)
    if (emails.length > 1) {
      event.preventDefault()
      emails.forEach((email) => onAdd(email))
      onDraftChange('')
    }
  }

  return (
    <div
      className={`um-chips-wrap${disabled ? ' is-disabled' : ''}`}
      onClick={() => inputRef.current?.focus()}
    >
      {chips.map((chip, index) => (
        <span
          key={`${chip}-${index}`}
          className={`um-chip${isValidEmail(chip) ? '' : ' is-invalid'}`}
        >
          <span className="um-chip-label">{chip}</span>
          {!disabled && (
            <button
              type="button"
              className="um-chip-remove"
              onClick={(event) => {
                event.stopPropagation()
                onRemove(index)
              }}
              aria-label={`Remove ${chip}`}
            >
              <i className="ti ti-x" aria-hidden="true"></i>
            </button>
          )}
        </span>
      ))}
      <input
        ref={inputRef}
        type="email"
        className="um-chips-input"
        placeholder={chips.length === 0 ? 'Type an email and press Enter or comma...' : ''}
        value={draft}
        onChange={(event) => handleChange(event.target.value)}
        onKeyDown={handleKeyDown}
        onBlur={commitDraft}
        onPaste={handlePaste}
        disabled={disabled}
        autoComplete="off"
        aria-label="Recipient email"
      />
    </div>
  )
}

function isValidEmail(email: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}
