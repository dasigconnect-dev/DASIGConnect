import { useEffect, useState, type RefObject } from "react";

interface InlineTagInputProps {
  tags: string[];
  value: string;
  onChange: (value: string) => void;
  onAdd: () => void;
  canAdd: boolean;
  onRemove: (tag: string) => void;
  disabled?: boolean;
  inputRef?: RefObject<HTMLInputElement | null>;
  placeholder?: string;
  emptyText?: string;
  addButtonLabel?: string;
}

export function InlineTagInput({
  tags,
  value,
  onChange,
  onAdd,
  canAdd,
  onRemove,
  disabled = false,
  inputRef,
  placeholder = "Type tag...",
  emptyText = "Click + to add tag",
  addButtonLabel = "Add tag",
}: InlineTagInputProps) {
  const [isAdding, setIsAdding] = useState(false);

  useEffect(() => {
    if (isAdding) {
      inputRef?.current?.focus();
    }
  }, [isAdding, inputRef]);

  function openInput() {
    if (disabled) return;
    setIsAdding(true);
    requestAnimationFrame(() => {
      inputRef?.current?.focus();
    });
  }

  return (
    <div
      className={`sub-inline-tags-box${isAdding ? " is-editing" : ""}${disabled ? " is-disabled" : ""}`}
      onClick={(event) => {
        if (disabled) return;
        if (event.target === event.currentTarget) {
          openInput();
        }
      }}
    >
      {tags.map((tag) => (
        <button
          key={tag}
          type="button"
          className="sub-tag active sub-inline-tag-chip"
          disabled={disabled}
          onClick={() => onRemove(tag)}
          title={disabled ? tag : `Remove ${tag}`}
        >
          <span>{tag}</span>
          {!disabled && <i className="ti ti-x" aria-hidden="true" />}
        </button>
      ))}

      {!disabled && (
        <>
          <input
            ref={inputRef}
            type="text"
            className={`sub-inline-tag-input${isAdding ? " is-open" : ""}`}
            value={value}
            readOnly={disabled}
            placeholder={placeholder}
            aria-label={addButtonLabel}
            onFocus={() => setIsAdding(true)}
            onChange={(event) => onChange(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter") {
                event.preventDefault();
                if (canAdd) {
                  onAdd();
                }
              } else if (event.key === "Escape") {
                event.preventDefault();
                onChange("");
                setIsAdding(false);
              }
            }}
            onBlur={() => {
              if (!value.trim()) {
                setIsAdding(false);
              }
            }}
          />

          <button
            type="button"
            className="sub-inline-tag-circle-btn"
            aria-label={addButtonLabel}
            title={isAdding && canAdd ? "Add tag" : addButtonLabel}
            onClick={() => {
              if (isAdding && canAdd) {
                onAdd();
                inputRef?.current?.focus();
              } else {
                openInput();
              }
            }}
          >
            <i className="ti ti-plus" aria-hidden="true" />
          </button>

          {!isAdding && tags.length === 0 && (
            <button
              type="button"
              className="sub-inline-tag-empty-trigger"
              onClick={openInput}
            >
              {emptyText}
            </button>
          )}
        </>
      )}

      {disabled && tags.length === 0 && (
        <span className="sub-muted-text">—</span>
      )}
    </div>
  );
}
