import { useRef } from "react";
import type { SubmissionMediaItem } from "../../types/media";

const ACCEPTED_TYPES = ["image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/quicktime", "video/webm"];
const MAX_MB = 50;

interface UploadMediaTabProps {
  onFilesAdded: (items: SubmissionMediaItem[]) => void;
  disabled?: boolean;
}

function fileToItem(file: File): SubmissionMediaItem {
  const isVideo = file.type.startsWith("video/");
  return {
    clientId: `upload-${file.name}-${file.lastModified}-${file.size}`,
    source: "upload",
    file,
    previewUrl: URL.createObjectURL(file),
    mediaType: isVideo ? "video" : "image",
    fileName: file.name,
  };
}

export default function UploadMediaTab({ onFilesAdded, disabled }: UploadMediaTabProps) {
  const inputRef = useRef<HTMLInputElement>(null);

  function processFiles(fileList: FileList | null) {
    if (!fileList || disabled) return;
    const valid: File[] = [];
    const oversized: string[] = [];

    Array.from(fileList).forEach((file) => {
      if (file.size > MAX_MB * 1024 * 1024) {
        oversized.push(file.name);
      } else {
        valid.push(file);
      }
    });

    if (oversized.length > 0) {
      // Surface in DOM — parent toast handles this
      console.warn(`Files exceed ${MAX_MB} MB:`, oversized.join(", "));
    }

    if (valid.length > 0) {
      onFilesAdded(valid.map(fileToItem));
    }
  }

  function handleDragOver(e: React.DragEvent<HTMLLabelElement>) {
    e.preventDefault();
    e.currentTarget.classList.add("umt-zone--drag-active");
  }

  function handleDragLeave(e: React.DragEvent<HTMLLabelElement>) {
    e.currentTarget.classList.remove("umt-zone--drag-active");
  }

  function handleDrop(e: React.DragEvent<HTMLLabelElement>) {
    e.preventDefault();
    e.currentTarget.classList.remove("umt-zone--drag-active");
    processFiles(e.dataTransfer.files);
  }

  return (
    <div className="umt-root">
      <label
        className={`umt-zone${disabled ? " umt-zone--disabled" : ""}`}
        onDragOver={handleDragOver}
        onDragLeave={handleDragLeave}
        onDrop={handleDrop}
        aria-label="Upload media files"
      >
        <input
          ref={inputRef}
          type="file"
          multiple
          accept={ACCEPTED_TYPES.join(",")}
          className="umt-input"
          onChange={(e) => {
            processFiles(e.target.files);
            e.target.value = "";
          }}
          disabled={disabled}
          aria-hidden
          tabIndex={-1}
        />
        <i className="ti ti-cloud-upload umt-icon" aria-hidden />
        <span className="umt-title">Drop files here or click to browse</span>
        <span className="umt-sub">Images and videos up to {MAX_MB} MB each</span>
        <div className="umt-types" aria-hidden>
          <span>JPG</span>
          <span>PNG</span>
          <span>WEBP</span>
          <span>GIF</span>
          <span>MP4</span>
          <span>MOV</span>
          <span>WEBM</span>
        </div>
      </label>
    </div>
  );
}
