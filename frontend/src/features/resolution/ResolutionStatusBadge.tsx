interface ResolutionStatusBadgeProps {
  manualPublishInProgress: boolean;
}

export default function ResolutionStatusBadge({
  manualPublishInProgress,
}: ResolutionStatusBadgeProps) {
  if (manualPublishInProgress) {
    return (
      <span className="status-badge status-manual">
        <i className="ti ti-user-check" aria-hidden="true" />
        Manual in Progress
      </span>
    );
  }

  return (
    <span className="status-badge status-failed">
      <i className="ti ti-alert-triangle" aria-hidden="true" />
      Publish Failed
    </span>
  );
}
