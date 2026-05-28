import { createPortal } from 'react-dom'
import { useEffect, useLayoutEffect, useRef, useState } from 'react'

export interface ActionMenuItem {
  label: string
  icon: string
  onClick: () => void
  disabled?: boolean
  dangerous?: boolean
  title?: string
}

interface ActionMenuProps {
  items: ActionMenuItem[]
  align?: 'left' | 'right'
}

interface DropdownCoords {
  top: number
  left: number
}

export default function ActionMenu({ items, align = 'right' }: ActionMenuProps) {
  const [isOpen, setIsOpen] = useState(false)
  const [coords, setCoords] = useState<DropdownCoords>({ top: 0, left: 0 })
  const triggerRef = useRef<HTMLButtonElement>(null)
  const dropdownRef = useRef<HTMLDivElement>(null)

  function openMenu() {
    if (!triggerRef.current) return
    const rect = triggerRef.current.getBoundingClientRect()
    // Will be refined by useLayoutEffect once the dropdown renders
    setCoords({ top: rect.bottom + 6, left: rect.left })
    setIsOpen(true)
  }

  // After the dropdown renders, shift it so it stays inside the viewport
  useLayoutEffect(() => {
    if (!isOpen || !dropdownRef.current || !triggerRef.current) return
    const triggerRect = triggerRef.current.getBoundingClientRect()
    const dropW = dropdownRef.current.offsetWidth
    const dropH = dropdownRef.current.offsetHeight
    const vw = window.innerWidth
    const vh = window.innerHeight
    const GAP = 6

    let top = triggerRect.bottom + GAP
    let left = align === 'right'
      ? triggerRect.right - dropW
      : triggerRect.left

    // Flip above trigger if it overflows the bottom
    if (top + dropH > vh - 8) {
      top = triggerRect.top - dropH - GAP
    }
    // Keep within left/right viewport edges
    left = Math.max(8, Math.min(left, vw - dropW - 8))

    setCoords({ top, left })
  }, [isOpen, align])

  useEffect(() => {
    if (!isOpen) return

    function handleOutsideClick(event: MouseEvent) {
      const target = event.target as Node
      if (
        triggerRef.current?.contains(target) ||
        dropdownRef.current?.contains(target)
      ) return
      setIsOpen(false)
    }

    function handleEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setIsOpen(false)
    }

    // Close when user scrolls so the menu doesn't detach from its row
    function handleScroll() {
      setIsOpen(false)
    }

    document.addEventListener('mousedown', handleOutsideClick)
    document.addEventListener('keydown', handleEscape)
    window.addEventListener('scroll', handleScroll, true)

    return () => {
      document.removeEventListener('mousedown', handleOutsideClick)
      document.removeEventListener('keydown', handleEscape)
      window.removeEventListener('scroll', handleScroll, true)
    }
  }, [isOpen])

  const dropdown = isOpen
    ? createPortal(
        <div
          ref={dropdownRef}
          className="um-action-dropdown"
          role="menu"
          style={{ top: coords.top, left: coords.left }}
        >
          {items.map((item, index) => (
            <button
              key={index}
              type="button"
              role="menuitem"
              className={`um-action-item${item.dangerous ? ' is-danger' : ''}${item.disabled ? ' is-disabled' : ''}`}
              disabled={item.disabled}
              title={item.title}
              onClick={() => {
                setIsOpen(false)
                item.onClick()
              }}
            >
              <i className={item.icon} aria-hidden="true" />
              <span>{item.label}</span>
            </button>
          ))}
        </div>,
        document.body,
      )
    : null

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        className={`um-action-trigger${isOpen ? ' is-open' : ''}`}
        onClick={() => (isOpen ? setIsOpen(false) : openMenu())}
        aria-label="Row actions"
        aria-expanded={isOpen}
        aria-haspopup="menu"
      >
        <i className="ti ti-dots-vertical" aria-hidden="true" />
      </button>
      {dropdown}
    </>
  )
}
