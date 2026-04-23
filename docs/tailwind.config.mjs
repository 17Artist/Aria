/** @type {import('tailwindcss').Config} */
export default {
  content: ['./src/**/*.{astro,html,js,jsx,md,mdx,svelte,ts,tsx,vue}'],
  theme: {
    extend: {
      colors: {
        aria: {
          bg: '#f5f6fb',
          'bg-code': '#1e2030',
          // 强调色加深
          accent: '#4361ee',
          'accent-light': '#5a75f0',
          'accent-wash': 'rgba(67,97,238,0.07)',
          'accent-wash-hover': 'rgba(67,97,238,0.12)',
          // 文字对比度提高
          ink: '#111827',
          'ink-secondary': '#374151',
          'ink-muted': '#6b7280',
          'ink-faint': '#9ca3af',
          // 边框加深
          line: '#d1d5e0',
          'line-faint': '#e5e7ef',
        },
      },
      fontFamily: {
        serif: ['"Playfair Display"', 'Georgia', 'serif'],
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['"JetBrains Mono"', '"Fira Code"', 'monospace'],
      },
      fontSize: {
        'hero': ['5rem', { lineHeight: '1', letterSpacing: '-0.04em' }],
        'hero-sm': ['3.5rem', { lineHeight: '1.05', letterSpacing: '-0.03em' }],
        'section': ['2.25rem', { lineHeight: '1.15', letterSpacing: '-0.02em' }],
      },
      animation: {
        'fade-in': 'fadeIn 0.9s ease-out forwards',
        'fade-up': 'fadeUp 0.9s ease-out forwards',
      },
      keyframes: {
        fadeIn: {
          from: { opacity: '0' },
          to: { opacity: '1' },
        },
        fadeUp: {
          from: { opacity: '0', transform: 'translateY(20px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
      },
    },
  },
  plugins: [],
};
