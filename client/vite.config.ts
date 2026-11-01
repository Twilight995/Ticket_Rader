import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 개발 중에는 /api 요청을 Spring Boot(8080)로 넘긴다. 배포 때는 같은 서버가 화면 파일을 제공한다.
    proxy: { '/api': 'http://localhost:8080' },
  },
})
