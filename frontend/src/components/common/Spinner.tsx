type SpinnerSize = 'xs' | 'sm' | 'md' | 'lg'
type SpinnerColor = 'inherit' | 'blue' | 'white' | 'muted'

interface SpinnerProps {
  size?: SpinnerSize
  color?: SpinnerColor
  className?: string
  'aria-label'?: string
}

export default function Spinner({
  size = 'sm',
  color = 'inherit',
  className = '',
  'aria-label': ariaLabel,
}: SpinnerProps) {
  const colorClass = color !== 'inherit' ? ` color-${color}` : ''
  return (
    <i
      className={`ti ti-loader-2 dc-spinner ${size}${colorClass}${className ? ` ${className}` : ''}`}
      aria-hidden={ariaLabel ? undefined : 'true'}
      aria-label={ariaLabel}
      role={ariaLabel ? 'img' : undefined}
    />
  )
}
