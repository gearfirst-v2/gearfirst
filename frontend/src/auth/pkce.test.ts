import { describe, expect, it } from 'vitest'
import { codeChallenge, randomString } from './pkce'

describe('pkce', () => {
  it('RFC 7636 부록 B의 예시 값과 같은 challenge를 만든다', async () => {
    expect(await codeChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')).toBe(
      'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM',
    )
  })

  it('verifier는 43자 이상의 URL 안전 문자이고 매번 다르다', () => {
    const a = randomString()
    expect(a).toMatch(/^[A-Za-z0-9_-]{43,128}$/)
    expect(randomString()).not.toBe(a)
  })
})
