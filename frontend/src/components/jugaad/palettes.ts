export type JugaadPalette = {
  name: string
  tagline: string
  bgFrom: string
  bgVia: string
  bgTo: string
  ringColor: string
  letterColor: string
  icons: string[]
}

export const PALETTES: Record<string, JugaadPalette> = {
  aurora: {
    name: 'Aurora',
    tagline: 'Default blue-violet',
    bgFrom: '#2A3CE8',
    bgVia: '#5B45F0',
    bgTo: '#9333EA',
    ringColor: '#A5B4FC',
    letterColor: '#FFFFFF',
    icons: ['#22D3A6', '#FBBF24', '#A78BFA', '#60A5FA', '#F59E0B', '#2DD4BF', '#FB7185', '#7DD3FC'],
  },
  sunset: {
    name: 'Sunset',
    tagline: 'Coral to magenta',
    bgFrom: '#FF6B6B',
    bgVia: '#F43F8E',
    bgTo: '#A21CAF',
    ringColor: '#FECACA',
    letterColor: '#FFF7ED',
    icons: ['#FFE66D', '#FFB088', '#FFFFFF', '#FCD34D', '#FF8FAB', '#F0ABFC', '#FFFFFF', '#FED7AA'],
  },
  neonMint: {
    name: 'Neon Mint',
    tagline: 'Cyber cyan-lime',
    bgFrom: '#022C22',
    bgVia: '#0E7490',
    bgTo: '#022C22',
    ringColor: '#5EEAD4',
    letterColor: '#ECFEFF',
    icons: ['#22D3EE', '#A3E635', '#34D399', '#67E8F9', '#FDE047', '#10B981', '#F472B6', '#A7F3D0'],
  },
  roseGold: {
    name: 'Rose Gold',
    tagline: 'Warm rose-amber',
    bgFrom: '#9D174D',
    bgVia: '#DB2777',
    bgTo: '#F59E0B',
    ringColor: '#FBCFE8',
    letterColor: '#FFF7ED',
    icons: ['#FDE68A', '#FFFFFF', '#FBCFE8', '#FED7AA', '#F0ABFC', '#FCA5A5', '#FFFFFF', '#FFE4E6'],
  },
  midnight: {
    name: 'Midnight',
    tagline: 'Mono slate-indigo',
    bgFrom: '#0F172A',
    bgVia: '#1E1B4B',
    bgTo: '#312E81',
    ringColor: '#64748B',
    letterColor: '#F8FAFC',
    icons: ['#38BDF8', '#FACC15', '#C084FC', '#22D3EE', '#FB923C', '#34D399', '#F472B6', '#94A3B8'],
  },
}

export const PALETTE_KEYS = Object.keys(PALETTES)
