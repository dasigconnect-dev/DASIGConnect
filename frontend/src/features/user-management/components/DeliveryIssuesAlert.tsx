import type { InviteResults } from '../types'

interface DeliveryIssuesAlertProps {
  results: InviteResults | null
  onResubmitFailed: () => void
}

export default function DeliveryIssuesAlert({
  results,
  onResubmitFailed,
}: DeliveryIssuesAlertProps) {
  if (!results) return null

  const hasFailures = results.failed.length > 0
  const hasSuccess = results.success.length > 0

  return (
    <div className={`um-delivery-result${hasFailures ? ' is-warning' : ' is-success'}`} role="status">
      <div className="um-delivery-result-icon">
        <i className={hasFailures ? 'ti ti-alert-triangle' : 'ti ti-circle-check'} aria-hidden="true"></i>
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div className="um-delivery-result-title">
          {hasFailures ? 'Delivery Issues' : 'Invitations Sent'}
        </div>
        <div className="um-delivery-result-copy">
          {hasFailures
            ? 'Some invitations need attention before the batch is complete.'
            : `${results.success.length} invitation${results.success.length === 1 ? '' : 's'} delivered successfully.`}
        </div>

        {hasSuccess && hasFailures && (
          <div style={{ marginTop: 8, fontSize: 12.5 }}>
            <strong>Sent:</strong> {results.success.join(', ')}
          </div>
        )}

        {hasFailures && (
          <details className="um-delivery-result-details">
            <summary>Show details ({results.failed.length} issue{results.failed.length !== 1 ? 's' : ''})</summary>
            <div className="um-delivery-failure-list">
              {results.failed.map((item) => (
                <div className="um-delivery-failure" key={`${item.email}-${item.reason}`}>
                  <strong>{item.email}</strong>
                  <span>{item.reason}</span>
                  {item.invitationUrl && (
                    <code className="um-invite-link">{item.invitationUrl}</code>
                  )}
                </div>
              ))}
            </div>
          </details>
        )}

        {hasFailures && results.failed.some((f) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(f.email)) && (
          <button type="button" className="um-delivery-result-action" onClick={onResubmitFailed}>
            Retry failed recipients only →
          </button>
        )}
      </div>
    </div>
  )
}
