/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: '#3b9eff',
        primaryDark: '#2d7fb8',
        secondary: '#1a1a2e',
        accent: '#0a9396',
        dark: {
          bg: '#0f0f1e',
          card: '#1a1a2e',
          border: '#2d2d44',
          text: '#e0e0e0',
        },
        status: {
          success: '#10b981',
          warning: '#f59e0b',
          error: '#ef4444',
          info: '#3b9eff',
        }
      },
      fontFamily: {
        sans: ['DM Sans', 'Roboto', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        card: '16px',
        pill: '9999px',
      },
      animation: {
        fadeUp: 'fadeUp 0.3s ease-out',
      },
      keyframes: {
        fadeUp: {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        }
      }
    },
  },
  plugins: [],
}
