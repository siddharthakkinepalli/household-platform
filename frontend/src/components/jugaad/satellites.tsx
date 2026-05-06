import React from 'react'

type SatProps = { color: string }

const COMMON = {
  fill: 'none',
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  strokeWidth: 6,
}

export const Cart: React.FC<SatProps> = ({ color }) => (
  <g {...COMMON} stroke={color}>
    <path d="M -22 -16 L -14 -16 L -8 8 L 16 8 L 22 -10 L -10 -10" />
    <circle cx="-6" cy="18" r="3" />
    <circle cx="14" cy="18" r="3" />
  </g>
)

export const Tray: React.FC<SatProps> = ({ color }) => (
  <g {...COMMON} stroke={color}>
    <path d="M -8 -22 Q -8 -16 -2 -16" />
    <path d="M 0 -22 Q 0 -16 6 -16" />
    <path d="M -22 0 Q 0 -22 22 0 Z" />
    <path d="M -26 6 L 26 6" />
  </g>
)

export const Calendar: React.FC<SatProps> = ({ color }) => (
  <g {...COMMON} stroke={color}>
    <rect x="-20" y="-16" width="40" height="36" rx="4" />
    <path d="M -20 -6 L 20 -6" />
    <path d="M -10 -22 L -10 -10" />
    <path d="M 10 -22 L 10 -10" />
    <circle cx="-6" cy="6" r="1.5" fill={color} stroke="none" />
    <circle cx="6" cy="6" r="1.5" fill={color} stroke="none" />
  </g>
)

export const Document: React.FC<SatProps> = ({ color }) => (
  <g {...COMMON} stroke={color}>
    <path d="M -16 -22 L 10 -22 L 20 -12 L 20 22 L -16 22 Z" />
    <path d="M 10 -22 L 10 -12 L 20 -12" />
    <path d="M -8 4 L 12 4" />
    <path d="M -8 14 L 12 14" />
  </g>
)

export const Folder: React.FC<SatProps> = ({ color }) => (
  <g {...COMMON} stroke={color}>
    <path d="M -22 -14 L -6 -14 L -2 -8 L 22 -8 L 22 18 L -22 18 Z" />
  </g>
)

export const Shield: React.FC<SatProps> = ({ color }) => (
  <g {...COMMON} stroke={color}>
    <path d="M 0 -22 L 18 -16 L 18 4 Q 18 18 0 22 Q -18 18 -18 4 L -18 -16 Z" />
    <path d="M -8 0 L -2 6 L 10 -6" />
  </g>
)

export const Bell: React.FC<SatProps> = ({ color }) => (
  <g {...COMMON} stroke={color}>
    <path d="M -16 8 Q -16 -16 0 -18 Q 16 -16 16 8 L 20 14 L -20 14 Z" />
    <path d="M -4 18 Q 0 22 4 18" />
    <path d="M 0 -22 L 0 -18" />
  </g>
)

export const Receipt: React.FC<SatProps> = ({ color }) => (
  <g {...COMMON} stroke={color}>
    <path d="M -14 -22 L 14 -22 L 14 22 L 8 18 L 2 22 L -4 18 L -10 22 L -14 18 Z" />
    <path d="M -8 -12 L 8 -12" />
    <path d="M -8 -4 L 4 -4" />
    <path d="M -4 -4 Q 4 -4 4 4 Q 4 8 -4 8 L 4 14" />
  </g>
)

export const SATELLITES = [Cart, Tray, Calendar, Document, Folder, Shield, Bell, Receipt]
