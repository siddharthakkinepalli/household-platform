import React from 'react'
import clsx from 'clsx'

interface CardProps {
  children: React.ReactNode
  className?: string
  hover?: boolean
  onClick?: () => void
}

export const Card: React.FC<CardProps> = ({ children, className, hover, onClick }) => (
  <div
    className={clsx(
      'card',
      hover && 'card-hover',
      onClick && 'cursor-pointer',
      className
    )}
    onClick={onClick}
  >
    {children}
  </div>
)

interface KPICardProps {
  title: string
  value: React.ReactNode
  change?: number
  icon?: string
  unit?: string
  trend?: 'up' | 'down' | 'neutral'
}

export const KPICard: React.FC<KPICardProps> = ({
  title,
  value,
  change,
  icon,
  unit,
  trend,
}) => (
  <Card className="text-center">
    {icon && <div className="text-4xl mb-2">{icon}</div>}
    <h3 className="text-dark-text/60 text-sm font-medium mb-2">{title}</h3>
    <div className="text-3xl font-bold text-primary mb-3">{value}</div>
    {unit && <div className="text-dark-text/40 text-xs mb-2">{unit}</div>}
    {change !== undefined && (
      <div className={clsx(
        'text-sm font-medium',
        trend === 'up' ? 'text-status-success' : trend === 'down' ? 'text-status-error' : 'text-dark-text/60'
      )}>
        {trend === 'up' ? '↑' : trend === 'down' ? '↓' : '→'} {Math.abs(change)}%
      </div>
    )}
  </Card>
)

interface BadgeProps {
  children: React.ReactNode
  variant?: 'success' | 'warning' | 'error' | 'info'
  className?: string
}

export const Badge: React.FC<BadgeProps> = ({ children, variant = 'info', className }) => (
  <span className={clsx(`badge badge-${variant}`, className)}>
    {children}
  </span>
)

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost'
  size?: 'sm' | 'md' | 'lg'
  loading?: boolean
  children: React.ReactNode
}

export const Button: React.FC<ButtonProps> = ({
  variant = 'primary',
  size = 'md',
  loading,
  children,
  className,
  disabled,
  ...props
}) => (
  <button
    className={clsx(
      `btn btn-${variant}`,
      size === 'sm' && 'px-3 py-1 text-xs',
      size === 'lg' && 'px-6 py-3 text-base',
      disabled && 'opacity-50 cursor-not-allowed',
      className
    )}
    disabled={disabled || loading}
    {...props}
  >
    {loading ? '⏳' : children}
  </button>
)

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string
  error?: string
}

export const Input: React.FC<InputProps> = ({ label, error, className, ...props }) => (
  <div className="mb-4">
    {label && <label className="block text-sm font-medium mb-2">{label}</label>}
    <input
      className={clsx(
        'w-full px-4 py-2 bg-dark-bg border border-dark-border rounded-lg text-dark-text placeholder-dark-text/30',
        'focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary',
        error && 'border-status-error',
        className
      )}
      {...props}
    />
    {error && <p className="text-status-error text-sm mt-1">{error}</p>}
  </div>
)

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  error?: string
  options: Array<{ value: string | number; label: string }>
}

export const Select: React.FC<SelectProps> = ({ label, error, options, className, ...props }) => (
  <div className="mb-4">
    {label && <label className="block text-sm font-medium mb-2">{label}</label>}
    <select
      className={clsx(
        'w-full px-4 py-2 bg-dark-bg border border-dark-border rounded-lg text-dark-text',
        'focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary',
        error && 'border-status-error',
        className
      )}
      {...props}
    >
      <option value="">Select...</option>
      {options.map((opt) => (
        <option key={opt.value} value={opt.value}>
          {opt.label}
        </option>
      ))}
    </select>
    {error && <p className="text-status-error text-sm mt-1">{error}</p>}
  </div>
)

interface LoaderProps {
  size?: 'sm' | 'md' | 'lg'
}

export const Loader: React.FC<LoaderProps> = ({ size = 'md' }) => {
  const sizeClass = size === 'sm' ? 'w-4 h-4' : size === 'lg' ? 'w-12 h-12' : 'w-8 h-8'
  return (
    <div className={clsx(sizeClass, 'animate-spin')}>
      <svg
        className="w-full h-full text-primary"
        xmlns="http://www.w3.org/2000/svg"
        fill="none"
        viewBox="0 0 24 24"
      >
        <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
        <path
          className="opacity-75"
          fill="currentColor"
          d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
        ></path>
      </svg>
    </div>
  )
}

interface EmptyStateProps {
  icon?: string
  title: string
  description?: string
  action?: {
    label: string
    onClick: () => void
  }
}

export const EmptyState: React.FC<EmptyStateProps> = ({ icon = '📭', title, description, action }) => (
  <div className="flex flex-col items-center justify-center py-12 text-center">
    <div className="text-6xl mb-4">{icon}</div>
    <h3 className="text-xl font-semibold mb-2">{title}</h3>
    {description && <p className="text-dark-text/60 mb-4">{description}</p>}
    {action && (
      <Button variant="primary" onClick={action.onClick}>
        {action.label}
      </Button>
    )}
  </div>
)
