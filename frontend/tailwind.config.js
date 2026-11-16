/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: 'var(--bg)',
        surface: 'var(--surface)',
        'surface-muted': 'var(--surface-muted)',
        border: 'var(--border)',
        'border-strong': 'var(--border-strong)',
        text: 'var(--text)',
        'text-muted': 'var(--text-muted)',
        accent: 'var(--accent)',
        'accent-contrast': 'var(--accent-contrast)',
        danger: 'var(--danger)',
        'danger-surface': 'var(--danger-surface)',
        success: 'var(--success)',
        'success-surface': 'var(--success-surface)',
      },
      fontFamily: {
        sans: ['var(--font-sans)'],
      },
      borderRadius: {
        card: '14px',
      },
      boxShadow: {
        pop: 'var(--shadow-pop)',
      },
      fontSize: {
        '2xs': ['11px', '14px'],
      },
    },
  },
  plugins: [],
};
