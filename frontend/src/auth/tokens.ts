import { CLIENT_ID, CLIENT_SECRET, GATEWAY_URL, redirectUri } from '../config'
import { codeChallenge, randomString } from './pkce'

export type TokenResponse = { access_token: string; refresh_token?: string; id_token?: string }

// 토큰은 메모리(이 변수)에만 둔다. localStorage·sessionStorage에 두면 XSS가 꺼내 가서 밖에서 쓸 수 있다.
// 새로고침하면 사라지지만, auth 로그인 세션이 살아 있으면 /login → authorize가 비밀번호 없이 다시 발급한다.
let tokens: TokenResponse | null = null

export const getTokens = () => tokens
export const setTokens = (t: TokenResponse | null) => {
  tokens = t
}

export async function requestToken(params: Record<string, string>): Promise<TokenResponse> {
  const res = await fetch('/auth/oauth2/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${btoa(`${CLIENT_ID}:${CLIENT_SECRET}`)}`,
    },
    body: new URLSearchParams(params),
  })
  if (!res.ok) throw new Error(`토큰 발급 실패 (${res.status})`)
  return res.json()
}

// auth는 refresh 토큰을 한 번 쓰면 버린다(회전). 갱신을 동시에 두 번 보내면 두 번째가 버려진 토큰을 써서 실패하므로,
// 진행 중인 갱신이 있으면 새로 보내지 않고 그 결과를 같이 기다린다.
let refreshing: Promise<boolean> | null = null

export function refreshToken(): Promise<boolean> {
  refreshing ??= (async () => {
    const refresh = tokens?.refresh_token
    if (!refresh) return false
    try {
      const next = await requestToken({ grant_type: 'refresh_token', refresh_token: refresh })
      tokens = { ...next, id_token: next.id_token ?? tokens?.id_token }
      return true
    } catch {
      tokens = null
      return false
    }
  })().finally(() => {
    refreshing = null
  })
  return refreshing
}

// access 토큰을 붙여 보내고, 401이면 한 번 갱신해서 다시 보낸다. 갱신도 실패하면 401 응답을 그대로 돌려준다.
export async function fetchWithAuth(url: string, init: RequestInit = {}): Promise<Response> {
  const send = () => {
    const headers = new Headers(init.headers)
    if (tokens) headers.set('Authorization', `Bearer ${tokens.access_token}`)
    return fetch(url, { ...init, headers })
  }
  const res = await send()
  if (res.status !== 401 || !(await refreshToken())) return res
  return send()
}

// verifier·state는 auth 로그인 페이지에 다녀오는 동안만 필요해서 sessionStorage에 두고, 콜백에서 바로 지운다.
// 개발 모드(StrictMode)는 useEffect를 두 번 실행한다. 두 번 시작하면 저장된 state와 이동한 주소의 state가 어긋날 수 있어 한 번만 시작한다
let loginStarting: Promise<void> | null = null

export function startLogin(): Promise<void> {
  loginStarting ??= redirectToAuthorize()
  return loginStarting
}

async function redirectToAuthorize(): Promise<void> {
  const verifier = randomString()
  const state = randomString(16)
  sessionStorage.setItem('pkce_verifier', verifier)
  sessionStorage.setItem('oauth_state', state)
  const params = new URLSearchParams({
    response_type: 'code',
    client_id: CLIENT_ID,
    redirect_uri: redirectUri(),
    scope: 'openid email offline_access',
    code_challenge: await codeChallenge(verifier),
    code_challenge_method: 'S256',
    state,
  })
  window.location.href = `${GATEWAY_URL}/auth/oauth2/authorize?${params}`
}

export async function finishLogin(search: string): Promise<void> {
  const params = new URLSearchParams(search)
  const verifier = sessionStorage.getItem('pkce_verifier')
  const state = sessionStorage.getItem('oauth_state')
  sessionStorage.removeItem('pkce_verifier')
  sessionStorage.removeItem('oauth_state')

  if (params.get('error')) throw new Error(`로그인 실패: ${params.get('error')}`)
  // state가 다르면 내가 시작하지 않은 로그인 응답이다(CSRF)
  if (!state || params.get('state') !== state) throw new Error('state가 맞지 않아요. 다시 로그인해 주세요.')
  const code = params.get('code')
  if (!code || !verifier) throw new Error('code 또는 verifier가 없어요. 다시 로그인해 주세요.')

  tokens = await requestToken({
    grant_type: 'authorization_code',
    code,
    redirect_uri: redirectUri(),
    code_verifier: verifier,
  })
}

// OIDC 로그아웃: auth 세션까지 끊어야 다음 로그인 때 비밀번호를 다시 묻는다.
// 돌아올 때 state가 붙어 오면 /login이 자동 로그인 대신 버튼을 보여준다.
export function logout(): void {
  const idToken = tokens?.id_token
  tokens = null
  if (!idToken) {
    window.location.href = '/login?state=logged-out'
    return
  }
  const params = new URLSearchParams({
    id_token_hint: idToken,
    post_logout_redirect_uri: `${window.location.origin}/login`,
    state: 'logged-out',
  })
  window.location.href = `${GATEWAY_URL}/auth/connect/logout?${params}`
}
