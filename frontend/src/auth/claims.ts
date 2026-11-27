// JWT 가운데 부분(payload)을 읽기만 한다. 서명 검증은 하지 않으므로 화면 표시에만 쓴다
export function decodeJwt(token: string): Record<string, unknown> {
  const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
  return JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(base64), (c) => c.charCodeAt(0))))
}

// access 토큰 클레임. gateway가 같은 값을 X-User-* 헤더로 user에 넘기고, user가 이 값으로 권한을 판단한다 (GEA-15)
export type Me = { id: string; rank: string; workType: string }

export function readMe(accessToken: string): Me {
  const claims = decodeJwt(accessToken)
  return { id: String(claims.sub), rank: String(claims.rank), workType: String(claims.work_type) }
}

// 버튼을 숨길 때만 쓴다. 진짜 차단은 서버의 403 (권한표: docs/security/B1-B2-user-authz.md)
export const canViewEmployees = (me: Me) => me.workType === '본사'
export const canManageEmployees = (me: Me) => me.workType === '본사' && me.rank === '팀장'
