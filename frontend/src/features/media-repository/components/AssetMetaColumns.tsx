import { formatFileSize, formatUploadDate, isVideoType } from "../utils";

interface AssetMetaColumnsProps {
  uploadedAt: string;
  fileSizeBytes: number;
  fileType: string;
  category?: string | null;
}

/**
 * Reusable list-view metadata columns: Date · Size · Type · Category.
 * Shared by the Media Library and Collections list views (and future list surfaces).
 */
export default function AssetMetaColumns({
  uploadedAt,
  fileSizeBytes,
  fileType,
  category,
}: AssetMetaColumnsProps) {
  const cols: Array<{ label: string; value: string }> = [
    { label: "Date", value: formatUploadDate(uploadedAt) },
    { label: "Size", value: formatFileSize(fileSizeBytes) },
    { label: "Type", value: isVideoType(fileType) ? "Video" : "Image" },
    { label: "Category", value: category && category.trim() ? category : "—" },
  ];

  return (
    <div className="asset-meta-cols">
      {cols.map((col) => (
        <div className="asset-meta-col" key={col.label}>
          <span className="asset-meta-label">{col.label}</span>
          <span className="asset-meta-val" title={col.value}>{col.value}</span>
        </div>
      ))}
    </div>
  );
}
