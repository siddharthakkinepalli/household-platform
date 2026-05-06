import React from 'react'
import { Link, useLocation } from 'react-router-dom'
import clsx from 'clsx'
import { JugaadIcon } from '@components/jugaad'

interface NavigationProps {
  open: boolean
  onClose: () => void
}

const navItems = [
  { label: 'Dashboard', path: '/', icon: '📊' },
  { label: 'Expenses', path: '/expenses', icon: '💰' },
  { label: 'Meals', path: '/meals', icon: '🍽️' },
  { label: 'Shopping', path: '/shopping', icon: '🛒' },
  { label: 'Reports', path: '/reports', icon: '📈' },
  { label: 'Settings', path: '/settings', icon: '⚙️' },
]

const Navigation: React.FC<NavigationProps> = ({ open, onClose }) => {
  const location = useLocation()
  const isActive = (path: string) => location.pathname === path || location.pathname.startsWith(path + '/')

  return (
    <>
      {/* Mobile Overlay */}
      {open && (
        <div
          className="fixed inset-0 bg-black/50 z-40 md:hidden"
          onClick={onClose}
        />
      )}

      {/* Sidebar */}
      <nav
        className={clsx(
          'fixed md:static inset-y-0 left-0 z-50 w-64 bg-dark-card border-r border-dark-border transform transition-transform duration-300 md:translate-x-0 md:w-64 flex flex-col',
          open ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        {/* Branding — visible on desktop */}
        <div className="hidden md:flex items-center gap-3 h-16 px-4 border-b border-dark-border">
          <JugaadIcon size={32} animated={false} showSatellites={false} className="shrink-0" />
          <span className="font-bold text-base">Jugaad Household</span>
        </div>

        {/* Header — mobile only */}
        <div className="flex items-center justify-between h-16 px-4 border-b border-dark-border md:hidden">
          <span className="font-bold text-lg">Menu</span>
          <button onClick={onClose} className="p-2 hover:bg-dark-border rounded-lg">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Navigation Items */}
        <div className="flex-1 overflow-y-auto px-3 py-4 space-y-2">
          {navItems.map((item) => (
            <Link
              key={item.path}
              to={item.path}
              onClick={onClose}
              className={clsx(
                'flex items-center gap-3 px-4 py-3 rounded-lg transition-all duration-200',
                isActive(item.path)
                  ? 'bg-primary/20 text-primary border border-primary/30'
                  : 'text-dark-text hover:bg-dark-border'
              )}
            >
              <span className="text-xl">{item.icon}</span>
              <span className="font-medium">{item.label}</span>
            </Link>
          ))}
        </div>

        {/* Footer */}
        <div className="border-t border-dark-border p-4">
          <div className="text-xs text-dark-text/60">
            <p>Jugaad Household v1.0</p>
            <p className="mt-1">© 2026</p>
          </div>
        </div>
      </nav>
    </>
  )
}

export default Navigation
