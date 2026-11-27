import { describe, expect, it } from 'vitest'
import { canManageEmployees, canViewEmployees, readMe } from './claims'

// 서명 없는 가짜 JWT. 한글 클레임이 깨지지 않는지도 본다
function fakeToken(claims: object): string {
  const json = new TextEncoder().encode(JSON.stringify(claims))
  const base64url = btoa(String.fromCharCode(...json)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
  return `h.${base64url}.s`
}

describe('readMe', () => {
  it('토큰 클레임에서 id·직급·근무유형을 읽는다', () => {
    expect(readMe(fakeToken({ sub: 7, rank: '팀장', work_type: '본사' }))).toEqual({
      id: '7',
      rank: '팀장',
      workType: '본사',
    })
  })
})

describe('버튼 표시 규칙 (권한표와 같음)', () => {
  const me = (workType: string, rank: string) => ({ id: '1', workType, rank })

  it.each([
    ['본사', '팀장', true, true],
    ['본사', '사원', true, false],
    ['창고', '창고 관리자', false, false],
    ['대리점', '팀장', false, false],
  ])('%s %s: 조회 %s, 관리 %s', (workType, rank, view, manage) => {
    expect(canViewEmployees(me(workType, rank))).toBe(view)
    expect(canManageEmployees(me(workType, rank))).toBe(manage)
  })
})
