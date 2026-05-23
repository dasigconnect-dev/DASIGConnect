import Spinner from './Spinner'

export default function PageLoader() {
  return (
    <div className="dc-page-loader" role="status" aria-label="Loading DASIGConnect">
      <div className="dc-page-loader-brand">
        <div className="dc-page-loader-icon">
          <svg viewBox="0 0 24 24" aria-hidden="true">
            <path d="M12 2L22 7V17L12 22L2 17V7L12 2Z" />
          </svg>
        </div>
        <div className="dc-page-loader-name">
          DASIG<em>Connect</em>
        </div>
      </div>
      <Spinner size="md" color="blue" className="dc-page-loader-spinner" aria-label="Loading" />
    </div>
  )
}
