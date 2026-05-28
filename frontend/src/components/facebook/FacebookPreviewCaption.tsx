interface FacebookPreviewCaptionProps {
  caption: string;
}

export default function FacebookPreviewCaption({
  caption,
}: FacebookPreviewCaptionProps) {
  const trimmed = caption.trim();

  return (
    <div
      className={`fb-preview-caption${trimmed ? "" : " is-empty"}`}
      aria-live="polite"
    >
      {trimmed || "Your caption preview will appear here."}
    </div>
  );
}
