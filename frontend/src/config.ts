// 값은 frontend/.env.local 에서 바꿀 수 있다 (.env.example 참고)
export const GATEWAY_URL = import.meta.env.VITE_GATEWAY_URL || 'http://localhost:8080'
export const CLIENT_ID = import.meta.env.VITE_CLIENT_ID || 'gearfirst-client'
// shortcut: 브라우저 번들에 들어간 secret은 누구나 볼 수 있어 비밀이 아니다(C5). auth를 공개 클라이언트로 바꾸면 지운다
export const CLIENT_SECRET = import.meta.env.VITE_CLIENT_SECRET || 'secret'

export const redirectUri = () => `${window.location.origin}/auth/callback`
