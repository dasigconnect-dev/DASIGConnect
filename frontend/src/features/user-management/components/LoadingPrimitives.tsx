interface SkeletonRowsProps {
  rows: number
  columns: number
}

export function InlineSpinner() {
  return <i className="ti ti-loader-2 um-spin" aria-hidden="true"></i>
}

export function SkeletonBlock({ className = '' }: { className?: string }) {
  return <span className={`um-skeleton ${className}`} aria-hidden="true"></span>
}

export function SkeletonRows({ rows, columns }: SkeletonRowsProps) {
  return (
    <>
      {Array.from({ length: rows }, (_, rowIndex) => (
        <tr className="um-skeleton-row" key={`skeleton-row-${rowIndex}`}>
          {Array.from({ length: columns }, (_, columnIndex) => (
            <td key={`skeleton-cell-${rowIndex}-${columnIndex}`}>
              <SkeletonBlock
                className={
                  columnIndex === 0
                    ? 'um-skeleton-line is-wide'
                    : columnIndex === columns - 1
                      ? 'um-skeleton-line is-short'
                      : 'um-skeleton-line'
                }
              />
            </td>
          ))}
        </tr>
      ))}
    </>
  )
}
