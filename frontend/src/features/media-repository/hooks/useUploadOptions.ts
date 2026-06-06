import { useCallback, useState } from "react";
import type { UploadDuplicateDecision } from "../components/UploadOptionsModal";

export interface ExistingNamedAsset {
  id: string;
  assetCode: string;
  fileName: string;
}

export interface UploadOptionsResult {
  proceed: boolean;
  decision: UploadDuplicateDecision;
  /** filename (lowercased) -> the existing asset it collides with. */
  duplicates: Map<string, ExistingNamedAsset>;
}

interface PendingDecision {
  duplicates: Map<string, ExistingNamedAsset>;
  resolve: (result: UploadOptionsResult) => void;
}

/**
 * Hook that gates an upload on duplicate-filename detection, Google Drive-style.
 *
 * `findExistingByName` resolves whether a filename already exists in the current location
 * (folder/collection/library scope). Call `confirmIfDuplicates(files)` before uploading: it
 * resolves immediately when nothing collides, or opens the Upload Options modal and resolves
 * once the user picks Replace / Keep both and confirms (or cancels). Spread `modal` into
 * `<UploadOptionsModal {...modal} />`.
 */
export function useUploadOptions(findExistingByName: (fileName: string) => ExistingNamedAsset | null) {
  const [pending, setPending] = useState<PendingDecision | null>(null);
  const [decision, setDecision] = useState<UploadDuplicateDecision>("keep_both");

  const confirmIfDuplicates = useCallback(
    (files: File[]): Promise<UploadOptionsResult> => {
      const duplicates = new Map<string, ExistingNamedAsset>();
      for (const file of files) {
        const existing = findExistingByName(file.name);
        if (existing) {
          duplicates.set(file.name.toLowerCase(), existing);
        }
      }
      if (duplicates.size === 0) {
        return Promise.resolve({ proceed: true, decision: "keep_both", duplicates });
      }
      setDecision("keep_both");
      return new Promise<UploadOptionsResult>((resolve) => setPending({ duplicates, resolve }));
    },
    [findExistingByName],
  );

  const handleConfirm = useCallback(() => {
    setPending((current) => {
      current?.resolve({ proceed: true, decision, duplicates: current.duplicates });
      return null;
    });
  }, [decision]);

  const handleCancel = useCallback(() => {
    setPending((current) => {
      current?.resolve({ proceed: false, decision, duplicates: current?.duplicates ?? new Map() });
      return null;
    });
  }, [decision]);

  const duplicateNames = pending
    ? Array.from(pending.duplicates.values()).map((a) => a.fileName)
    : [];

  return {
    confirmIfDuplicates,
    modal: {
      open: pending !== null,
      duplicateNames,
      decision,
      onDecisionChange: setDecision,
      onConfirm: handleConfirm,
      onCancel: handleCancel,
    },
  };
}
