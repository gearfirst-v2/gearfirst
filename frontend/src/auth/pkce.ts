// PKCE(RFC 7636): 로그인할 때 임의의 verifier를 만들고, 그 SHA-256 해시(challenge)만 auth에 먼저 보낸다.
// 토큰을 받을 때 verifier 원본을 보내서, 로그인을 시작한 브라우저가 맞는지 auth가 확인한다.

function base64url(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}

// 원본은 Math.random을 썼다. 예측할 수 있는 난수라 보안 값에는 crypto를 쓴다
export function randomString(byteLength = 32): string {
  return base64url(crypto.getRandomValues(new Uint8Array(byteLength)))
}

export async function codeChallenge(verifier: string): Promise<string> {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
  return base64url(new Uint8Array(digest))
}
