import { useEffect } from 'react'
import { startLogin } from '../auth/tokens'

export default function Login() {
  // OIDC 로그아웃에서 돌아온 경우(state=logged-out)에는 바로 다시 로그인되지 않게 버튼을 보여준다
  const loggedOut = new URLSearchParams(window.location.search).get('state') === 'logged-out'

  useEffect(() => {
    if (!loggedOut) void startLogin()
  }, [loggedOut])

  if (!loggedOut) return <p>로그인 페이지로 이동하고 있어요…</p>
  return (
    <main>
      <p>로그아웃했어요.</p>
      <button type="button" onClick={() => void startLogin()}>
        다시 로그인
      </button>
    </main>
  )
}
