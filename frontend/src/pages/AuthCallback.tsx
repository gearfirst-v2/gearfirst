import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { finishLogin } from '../auth/tokens'

// 개발 모드(StrictMode)는 useEffect를 두 번 실행한다. code는 한 번만 쓸 수 있어서 교환 요청을 하나로 묶는다
let exchange: Promise<void> | null = null

export default function AuthCallback() {
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    exchange ??= finishLogin(window.location.search)
    exchange.then(
      () => navigate('/', { replace: true }),
      (e: Error) => setError(e.message),
    )
  }, [navigate])

  if (!error) return <p>로그인 처리 중이에요…</p>
  return (
    <main>
      <p role="alert">{error}</p>
      <Link to="/login">로그인 다시 하기</Link>
    </main>
  )
}
