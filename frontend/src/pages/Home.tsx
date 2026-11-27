import { type FormEvent, useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { fetchWithAuth, getTokens, logout } from '../auth/tokens'

// 백엔드 공통 응답 형식 (auth·user의 ApiResponse)
type ApiResponse<T> = { status: number; success: boolean; message: string; data?: T }

// auth ChangePasswordRequest의 @Pattern과 같은 규칙. 화면에서 먼저 알려주고, 최종 검사는 서버가 한다
const PASSWORD_RULE = /^(?=.*[A-Za-z])(?=.*\d)(?=.*[@$!%*#?&])[A-Za-z\d@$!%*#?&]{8,20}$/

// JWT 가운데 부분(payload)을 읽기만 한다. 서명 검증은 하지 않으므로 화면 표시에만 쓴다
function decodeJwt(token: string): unknown {
  const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
  return JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(base64), (c) => c.charCodeAt(0))))
}

export default function Home() {
  const navigate = useNavigate()
  const [me, setMe] = useState<string>('불러오는 중…')
  const idToken = getTokens()?.id_token

  useEffect(() => {
    fetchWithAuth('/user/api/v1/me').then(async (res) => {
      if (res.status === 401) return navigate('/login', { replace: true })
      setMe(JSON.stringify(await res.json(), null, 2))
    })
  }, [navigate])

  return (
    <main>
      <h1>GearFirst 개발용 화면</h1>
      <button type="button" onClick={logout}>
        로그아웃
      </button>

      <h2>내 정보 (/user/api/v1/me)</h2>
      <pre>{me}</pre>

      <h2>id 토큰 내용 (표시용)</h2>
      <pre>{idToken ? JSON.stringify(decodeJwt(idToken), null, 2) : 'id 토큰 없음'}</pre>

      <ChangePassword />
    </main>
  )
}

function ChangePassword() {
  const [message, setMessage] = useState<string | null>(null)

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const form = new FormData(e.currentTarget)
    const body = {
      currentPassword: String(form.get('currentPassword')),
      newPassword: String(form.get('newPassword')),
      confirmPassword: String(form.get('confirmPassword')),
    }
    if (!PASSWORD_RULE.test(body.newPassword)) {
      return setMessage('새 비밀번호는 8~20자이며, 영문자·숫자·특수문자(@$!%*#?&)를 모두 포함해야 해요.')
    }
    if (body.newPassword !== body.confirmPassword) return setMessage('새 비밀번호와 확인이 달라요.')

    // userId는 보내지 않는다. 서버가 토큰의 sub로 본인을 확인한다 (C3)
    const res = await fetchWithAuth('/auth/api/v1/auth/change-password', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    const result = (await res.json().catch(() => null)) as ApiResponse<unknown> | null
    // 400(현재 비밀번호 틀림), 423(여러 번 틀려 잠김) 모두 서버 message를 그대로 보여준다
    setMessage(res.ok ? '비밀번호를 바꿨어요.' : `${res.status}: ${result?.message ?? res.statusText}`)
  }

  return (
    <form onSubmit={onSubmit}>
      <h2>비밀번호 변경</h2>
      <label>
        현재 비밀번호 <input name="currentPassword" type="password" autoComplete="current-password" required />
      </label>
      <label>
        새 비밀번호 <input name="newPassword" type="password" autoComplete="new-password" required />
      </label>
      <label>
        새 비밀번호 확인 <input name="confirmPassword" type="password" autoComplete="new-password" required />
      </label>
      <button type="submit">변경</button>
      {message && <p role="status">{message}</p>}
    </form>
  )
}
