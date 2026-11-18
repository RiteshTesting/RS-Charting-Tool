/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,jsx,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: 'var(--bg-app)',
        surface: 'var(--bg-card)',
        elevated: 'var(--bg-elevated)',
        border: 'var(--border-subtle)',
        bull: '#10B981',
        bear: '#EF4444',
        ema20: '#22D3EE',
        ema45: '#F59E0B',
        ema200: '#A855F7',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'Roboto', 'sans-serif'],
        mono: ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'Monaco', 'Consolas', 'monospace'],
      },
    },
  },
  plugins: [],
};
