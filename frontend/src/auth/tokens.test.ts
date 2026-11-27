import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchWithAuth, getTokens, setTokens } from './tokens'

afterEach(() => {
  vi.unstubAllGlobals()
  setTokens(null)
})

// access 토큰 'old'는 만료(401), 'new'만 통과하는 가짜 서버
function fakeServer() {
  const tokenCalls: string[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string, init: RequestInit) => {
      if (url === '/auth/oauth2/token') {
        tokenCalls.push(String(init.body))
        await new Promise((r) => setTimeout(r, 10)) // 갱신이 끝나기 전에 다른 요청도 401을 받게 한다
        return Response.json({ access_token: 'new', refresh_token: 'r2' })
      }
      const auth = new Headers(init.headers).get('Authorization')
      return new Response(null, { status: auth === 'Bearer new' ? 200 : 401 })
    }),
  )
  return tokenCalls
}

describe('fetchWithAuth', () => {
  it('401이 동시에 나도 refresh는 한 번만 보내고 둘 다 새 토큰으로 성공한다', async () => {
    const tokenCalls = fakeServer()
    setTokens({ access_token: 'old', refresh_token: 'r1' })

    const results = await Promise.all([fetchWithAuth('/user/a'), fetchWithAuth('/user/b')])

    expect(results.map((r) => r.status)).toEqual([200, 200])
    expect(tokenCalls).toHaveLength(1)
    expect(getTokens()?.refresh_token).toBe('r2')
  })

  it('refresh 토큰이 없으면 갱신하지 않고 401을 그대로 돌려준다', async () => {
    const tokenCalls = fakeServer()
    setTokens({ access_token: 'old' })

    const res = await fetchWithAuth('/user/a')

    expect(res.status).toBe(401)
    expect(tokenCalls).toHaveLength(0)
  })
})
