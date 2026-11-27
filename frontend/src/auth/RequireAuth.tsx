import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { getTokens } from './tokens'

// 토큰이 메모리에만 있어서, 새로고침 직후에는 항상 없다 → /login이 다시 발급받아 온다
export default function RequireAuth({ children }: { children: ReactNode }) {
  return getTokens() ? children : <Navigate to="/login" replace />
}
