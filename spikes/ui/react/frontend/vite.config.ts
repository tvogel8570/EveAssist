import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// `npm run build` writes straight into the Spring Boot app's static resources.
// `npm run dev` serves the app with hot reload on port 5173 and forwards /api to Spring Boot on 8092.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  build: {
    outDir: '../target/classes/static',
    emptyOutDir: true,
  },
  server: {
    proxy: { '/api': 'http://localhost:8092' },
  },
})
