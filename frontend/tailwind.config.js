/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      fontFamily: {
        sans: ['"PingFang SC"', '"Microsoft YaHei"', 'system-ui', 'sans-serif'],
        display: ['"PingFang SC"', '"SF Pro Display"', 'system-ui', 'sans-serif'],
        mono: ['"SF Mono"', '"JetBrains Mono"', 'Menlo', 'monospace'],
      },
      colors: {
        // brand 紫色主色调
        brand: {
          50: '#faf5ff', 100: '#f3e8ff', 200: '#e9d5ff', 300: '#d8b4fe',
          400: '#c084fc', 500: '#a855f7', 600: '#9333ea', 700: '#7e22ce',
          800: '#6b21a8', 900: '#581c87', 950: '#3b0764',
        },
        // 科幻霓虹配色（purple/violet 为主色）
        neon: {
          blue: '#00d4ff',
          cyan: '#22fff7',
          purple: '#a855f7',
          violet: '#8b5cf6',
          pink: '#ec4899',
          green: '#39ff14',
          amber: '#ffb800',
        },
        // 深色科幻底色（深紫黑基调）
        space: {
          900: '#0a0414',
          800: '#1a0b2e',
          700: '#2d1b4e',
          600: '#3b2266',
          500: '#4a2d80',
        },
      },
      backgroundImage: {
        'grid-glow': "linear-gradient(rgba(168, 85, 247, 0.07) 1px, transparent 1px), linear-gradient(90deg, rgba(168, 85, 247, 0.07) 1px, transparent 1px)",
        'radial-neon': 'radial-gradient(circle at 50% 0%, rgba(168, 85, 247, 0.15), transparent 60%)',
        'space-gradient': 'linear-gradient(135deg, #0a0414 0%, #1a0b2e 40%, #2d1b4e 100%)',
      },
      backdropBlur: {
        xs: '2px',
        '2xl': '32px',
        '3xl': '48px',
      },
      boxShadow: {
        'neon-blue': '0 0 12px rgba(168, 85, 247, 0.45), 0 0 24px rgba(168, 85, 247, 0.25)',
        'neon-purple': '0 0 12px rgba(168, 85, 247, 0.45), 0 0 24px rgba(168, 85, 247, 0.25)',
        'neon-cyan': '0 0 12px rgba(34, 255, 247, 0.45), 0 0 24px rgba(34, 255, 247, 0.25)',
        'neon-inset': 'inset 0 0 12px rgba(168, 85, 247, 0.25)',
        'glass': '0 8px 32px rgba(0, 0, 0, 0.37), inset 0 1px 0 rgba(255, 255, 255, 0.05)',
        'glow-sm': '0 0 8px rgba(168, 85, 247, 0.4)',
        'glow-md': '0 0 16px rgba(168, 85, 247, 0.5), 0 0 32px rgba(168, 85, 247, 0.2)',
        'glow-lg': '0 0 24px rgba(168, 85, 247, 0.6), 0 0 48px rgba(168, 85, 247, 0.3)',
      },
      animation: {
        'fade-in': 'fadeIn 0.3s ease-out',
        'slide-up': 'slideUp 0.35s cubic-bezier(0.16, 1, 0.3, 1)',
        'pulse-slow': 'pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'glow': 'glow 2s ease-in-out infinite',
        'glow-purple': 'glowPurple 2.5s ease-in-out infinite',
        'float': 'float 4s ease-in-out infinite',
        'float-slow': 'float 7s ease-in-out infinite',
        'rotate-slow': 'rotateSlow 18s linear infinite',
        'rotate-reverse': 'rotateReverse 22s linear infinite',
        'shimmer': 'shimmer 2.5s linear infinite',
        'particle': 'particle 8s ease-in-out infinite',
        'pulse-ring': 'pulseRing 2.4s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'gradient-shift': 'gradientShift 8s ease infinite',
        'msg-in': 'msgIn 0.4s cubic-bezier(0.16, 1, 0.3, 1)',
        'card-in': 'cardIn 0.5s cubic-bezier(0.16, 1, 0.3, 1)',
        'blink-cursor': 'blinkCursor 1s step-end infinite',
        'scan': 'scan 3s linear infinite',
        'border-glow': 'borderGlow 2s ease-in-out infinite',
      },
      keyframes: {
        fadeIn: { '0%': { opacity: 0 }, '100%': { opacity: 1 } },
        slideUp: {
          '0%': { opacity: 0, transform: 'translateY(12px)' },
          '100%': { opacity: 1, transform: 'translateY(0)' },
        },
        glow: {
          '0%, 100%': { boxShadow: '0 0 12px rgba(168, 85, 247, 0.45), 0 0 24px rgba(168, 85, 247, 0.2)' },
          '50%': { boxShadow: '0 0 20px rgba(168, 85, 247, 0.75), 0 0 40px rgba(168, 85, 247, 0.4)' },
        },
        glowPurple: {
          '0%, 100%': { boxShadow: '0 0 12px rgba(168, 85, 247, 0.45), 0 0 24px rgba(168, 85, 247, 0.2)' },
          '50%': { boxShadow: '0 0 20px rgba(168, 85, 247, 0.75), 0 0 40px rgba(168, 85, 247, 0.4)' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0)' },
          '50%': { transform: 'translateY(-12px)' },
        },
        rotateSlow: {
          '0%': { transform: 'rotate(0deg)' },
          '100%': { transform: 'rotate(360deg)' },
        },
        rotateReverse: {
          '0%': { transform: 'rotate(360deg)' },
          '100%': { transform: 'rotate(0deg)' },
        },
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        particle: {
          '0%': { transform: 'translate(0, 0) scale(1)', opacity: 0.4 },
          '50%': { transform: 'translate(20px, -30px) scale(1.2)', opacity: 0.9 },
          '100%': { transform: 'translate(0, 0) scale(1)', opacity: 0.4 },
        },
        pulseRing: {
          '0%': { transform: 'scale(0.8)', opacity: 0.8 },
          '100%': { transform: 'scale(2.2)', opacity: 0 },
        },
        gradientShift: {
          '0%, 100%': { backgroundPosition: '0% 50%' },
          '50%': { backgroundPosition: '100% 50%' },
        },
        msgIn: {
          '0%': { opacity: 0, transform: 'translateY(14px) scale(0.98)' },
          '100%': { opacity: 1, transform: 'translateY(0) scale(1)' },
        },
        cardIn: {
          '0%': { opacity: 0, transform: 'scale(0.92)' },
          '100%': { opacity: 1, transform: 'scale(1)' },
        },
        blinkCursor: {
          '0%, 50%': { opacity: 1 },
          '51%, 100%': { opacity: 0 },
        },
        scan: {
          '0%': { transform: 'translateY(-100%)' },
          '100%': { transform: 'translateY(100%)' },
        },
        borderGlow: {
          '0%, 100%': { borderColor: 'rgba(168, 85, 247, 0.4)' },
          '50%': { borderColor: 'rgba(192, 132, 252, 0.7)' },
        },
      },
    },
  },
  plugins: [],
};
