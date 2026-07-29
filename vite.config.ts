import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  base: '/treino/',
  plugins: [
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,ico,json,woff2}'],
        navigateFallback: '/treino/index.html',
      },
      manifest: {
        name: 'Treino',
        short_name: 'Treino',
        description: 'Acompanhamento de treino na academia — registro de séries e progressão.',
        lang: 'pt-BR',
        display: 'standalone',
        orientation: 'portrait',
        background_color: '#0a0b0e',
        theme_color: '#0a0b0e',
        start_url: '/treino/',
        scope: '/treino/',
        icons: [
          { src: 'pwa-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'pwa-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
    }),
  ],
})
