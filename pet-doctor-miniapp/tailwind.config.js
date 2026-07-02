/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        cream: '#fcfbf9',
        'user-bubble': '#fff8db',
        accent: '#f97316',
      },
    },
  },
  plugins: [],
  corePlugins: {
    preflight: false,
  },
}
