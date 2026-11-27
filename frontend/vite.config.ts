/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const gateway = loadEnv(mode, process.cwd(), 'VITE_').VITE_GATEWAY_URL || 'http://localhost:8080'
  const toGateway = { target: gateway, changeOrigin: true }

  return {
    plugins: [react()],
    server: {
      port: 5173,
      strictPort: true, // auth에 등록된 redirect URI가 5173이라, 다른 포트로 뜨면 로그인이 깨진다
      // 브라우저는 5173에만 요청하고 Vite가 gateway로 넘긴다. 같은 출처라 CORS가 필요 없다.
      // /auth 전체가 아니라 API만 넘긴다: /auth/callback은 프론트 화면이라 넘기면 안 된다
      proxy: {
        '/auth/oauth2': toGateway,
        '/auth/api': toGateway,
        '/user': toGateway,
        '/order': toGateway,
      },
    },
  }
})
