import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import path from 'path'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Serve the font folder as a static asset so /font/ URLs resolve correctly
  assetsInclude: ['**/*.ttf', '**/*.otf'],
  server: {
    fs: {
      allow: ['..'],
    },
  },
})

