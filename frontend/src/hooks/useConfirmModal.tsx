import { useCallback, useRef, useState } from "react";
import ConfirmModal, { type ConfirmOptions } from "../components/ConfirmModal";

export type { ConfirmOptions };

/**
 * Hook-driven confirmation modal. Call `confirm(options, onConfirm)` to open it; the modal
 * keeps itself open and shows a busy state while `onConfirm` runs (await-able), then closes.
 * Render the returned `modal` once in the consuming component.
 */
export function useConfirmModal() {
  const [options, setOptions] = useState<ConfirmOptions | null>(null);
  const [busy, setBusy] = useState(false);
  const actionRef = useRef<(() => void | Promise<void>) | null>(null);

  const confirm = useCallback((opts: ConfirmOptions, onConfirm: () => void | Promise<void>) => {
    actionRef.current = onConfirm;
    setOptions(opts);
  }, []);

  const cancel = useCallback(() => {
    if (busy) return;
    actionRef.current = null;
    setOptions(null);
  }, [busy]);

  const runConfirm = useCallback(async () => {
    const action = actionRef.current;
    if (!action) return;
    setBusy(true);
    try {
      await action();
      actionRef.current = null;
      setOptions(null);
    } finally {
      setBusy(false);
    }
  }, []);

  const modal = options ? (
    <ConfirmModal {...options} busy={busy} onConfirm={runConfirm} onCancel={cancel} />
  ) : null;

  return { confirm, modal, busy };
}
