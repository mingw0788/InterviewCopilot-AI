import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const proxy = {
  '/api': {
    target: process.env.BUSINESS_API_PROXY_TARGET ?? 'http://127.0.0.1:8080',
    changeOrigin: true,
  },
}

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '127.0.0.1',
    strictPort: true,
    proxy,
  },
  preview: { host: '127.0.0.1', strictPort: true, proxy },
})
