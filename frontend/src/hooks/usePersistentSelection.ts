import { useCallback, useState } from "react";

/**
 * Reusable multi-select state that survives navigation and reloads within the
 * same browser tab by persisting selected IDs to sessionStorage. Pass a unique
 * storageKey per selection context (e.g. "dasigconnect:media-selection").
 */
export interface PersistentSelection {
  selected: Set<string>;
  size: number;
  has: (id: string) => boolean;
  toggle: (id: string) => void;
  clear: () => void;
  setSelection: (ids: Iterable<string>) => void;
}

function readSelection(key: string): Set<string> {
  try {
    const raw = sessionStorage.getItem(key);
    if (!raw) return new Set();
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return new Set();
    return new Set(parsed.filter((value): value is string => typeof value === "string"));
  } catch {
    return new Set();
  }
}

function writeSelection(key: string, ids: Set<string>) {
  try {
    if (ids.size === 0) sessionStorage.removeItem(key);
    else sessionStorage.setItem(key, JSON.stringify([...ids]));
  } catch {
    // sessionStorage unavailable (private mode / quota) — keep selection in memory only.
  }
}

export function usePersistentSelection(storageKey: string): PersistentSelection {
  const [selected, setSelected] = useState<Set<string>>(() => readSelection(storageKey));

  const commit = useCallback(
    (next: Set<string>) => {
      writeSelection(storageKey, next);
      setSelected(next);
    },
    [storageKey],
  );

  const toggle = useCallback(
    (id: string) => {
      setSelected((prev) => {
        const next = new Set(prev);
        if (next.has(id)) next.delete(id);
        else next.add(id);
        writeSelection(storageKey, next);
        return next;
      });
    },
    [storageKey],
  );

  const clear = useCallback(() => commit(new Set()), [commit]);
  const setSelection = useCallback(
    (ids: Iterable<string>) => commit(new Set(ids)),
    [commit],
  );
  const has = useCallback((id: string) => selected.has(id), [selected]);

  return { selected, size: selected.size, has, toggle, clear, setSelection };
}
