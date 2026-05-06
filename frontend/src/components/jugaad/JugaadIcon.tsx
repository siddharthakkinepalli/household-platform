import React, { CSSProperties, useId } from 'react'
import { PALETTES, PALETTE_KEYS } from './palettes'
import { SATELLITES } from './satellites'
import './jugaad.css'

type JugaadShape = 'squircle' | 'circle' | 'rounded'

type JugaadIconProps = {
  palette?: string
  shape?: JugaadShape
  size?: number
  animated?: boolean
  showSatellites?: boolean
  showRing?: boolean
  className?: string
  style?: CSSProperties
}

const JugaadIcon: React.FC<JugaadIconProps> = ({
  palette = 'aurora',
  shape = 'squircle',
  size = 256,
  animated = true,
  showSatellites = true,
  showRing = true,
  className = '',
  style = {},
}) => {
  const uid = useId().replace(/:/g, '')
  const p = PALETTES[palette] || PALETTES.aurora
  const radius = shape === 'circle' ? 256 : shape === 'rounded' ? 64 : 120

  const cx = 256
  const cy = 256
  const ringR = 188

  const positions = Array.from({ length: 8 }).map((_, i) => {
    const angle = (-90 + i * 45) * (Math.PI / 180)
    return { x: cx + ringR * Math.cos(angle), y: cy + ringR * Math.sin(angle) }
  })

  return (
    <div
      data-testid={`jugaad-icon-${palette}-${shape}`}
      className={`jugaad-icon-wrap ${animated ? 'is-animated' : ''} ${className}`}
      style={{ width: size, height: size, ...style }}
    >
      <svg viewBox="0 0 512 512" width="100%" height="100%" xmlns="http://www.w3.org/2000/svg">
        <defs>
          <linearGradient id={`bg-${uid}`} x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor={p.bgFrom} />
            <stop offset="55%" stopColor={p.bgVia} />
            <stop offset="100%" stopColor={p.bgTo} />
          </linearGradient>

          <radialGradient id={`gloss-${uid}`} cx="30%" cy="20%" r="60%">
            <stop offset="0%" stopColor="#ffffff" stopOpacity="0.35" />
            <stop offset="60%" stopColor="#ffffff" stopOpacity="0" />
          </radialGradient>

          <radialGradient id={`glow-${uid}`} cx="50%" cy="55%" r="45%">
            <stop offset="0%" stopColor="#ffffff" stopOpacity="0.18" />
            <stop offset="100%" stopColor="#ffffff" stopOpacity="0" />
          </radialGradient>

          <filter id={`jshadow-${uid}`} x="-20%" y="-20%" width="140%" height="140%">
            <feGaussianBlur in="SourceAlpha" stdDeviation="6" />
            <feOffset dx="0" dy="6" result="off" />
            <feComponentTransfer>
              <feFuncA type="linear" slope="0.35" />
            </feComponentTransfer>
            <feMerge>
              <feMergeNode />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>

          <clipPath id={`clip-${uid}`}>
            <rect x="0" y="0" width="512" height="512" rx={radius} ry={radius} />
          </clipPath>
        </defs>

        <g clipPath={`url(#clip-${uid})`}>
          <rect width="512" height="512" fill={`url(#bg-${uid})`} />
          <rect width="512" height="512" fill={`url(#glow-${uid})`} />
          <rect width="512" height="512" fill={`url(#gloss-${uid})`} />

          <g opacity="0.18" fill="#ffffff">
            {Array.from({ length: 24 }).map((_, i) => (
              <circle
                key={i}
                cx={(i * 79) % 512}
                cy={(i * 137) % 512}
                r={(i % 3) + 0.6}
              />
            ))}
          </g>

          {showRing && (
            <circle
              className="jg-ring"
              cx={cx}
              cy={cy}
              r={ringR}
              fill="none"
              stroke={p.ringColor}
              strokeOpacity="0.55"
              strokeWidth="2.5"
              strokeDasharray="5 9"
            />
          )}

          {showSatellites && (
            <g className="jg-orbit">
              {positions.map((pos, i) => {
                const Icon = SATELLITES[i]
                return (
                  <g key={i} transform={`translate(${pos.x} ${pos.y})`}>
                    <g className="jg-sat" style={{ animationDelay: `${i * 0.12}s` }}>
                      <circle r="22" fill="#ffffff" fillOpacity="0.06" />
                      <Icon color={p.icons[i]} />
                    </g>
                  </g>
                )
              })}
            </g>
          )}

          <g className="jg-letter" filter={`url(#jshadow-${uid})`} style={{ transformOrigin: '256px 256px' }}>
            <path
              d="M 178 138 L 334 138 Q 354 138 354 158 L 354 178 Q 354 198 334 198 L 290 198 L 290 312 Q 290 384 224 384 Q 158 384 158 312 L 158 296 Q 158 280 174 280 L 198 280 Q 214 280 214 296 L 214 312 Q 214 332 224 332 Q 234 332 234 312 L 234 198 L 178 198 Q 158 198 158 178 L 158 158 Q 158 138 178 138 Z"
              fill={p.letterColor}
            />
          </g>
        </g>
      </svg>
    </div>
  )
}

export { PALETTES, PALETTE_KEYS }
export default JugaadIcon
