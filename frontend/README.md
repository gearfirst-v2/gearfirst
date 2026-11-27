# frontend

개발할 때 쓰는 최소 화면이에요(로그인, 내 정보, 비밀번호 변경, 로그아웃). React + TypeScript + Vite.

## 실행

1. 백엔드: `cd infra && docker compose up -d --build` (gateway 8080)
2. 프론트: `cd frontend && npm install && npm run dev`
3. http://localhost:5173 접속

포트는 5173 고정이에요. auth에 등록된 redirect URI가 `http://localhost:5173/auth/callback`이라서요.

## 명령

- `npm run dev`: 개발 서버
- `npm test`: 테스트(vitest)
- `npm run build`: 타입 검사 + 빌드 (`dist/`)

## 설정

기본값으로 바로 동작해요. 바꿀 때만 `.env.example`을 `.env.local`로 복사해 고쳐요.

## 동작 방식

- 로그인: OAuth2 Authorization Code + PKCE. `/login` → auth 로그인 페이지 → `/auth/callback`에서 토큰 발급.
- 토큰: access·refresh·id 모두 메모리에만 둬요. 새로고침하면 auth 세션으로 다시 받아요.
- API: 브라우저는 5173에만 요청하고, Vite 프록시가 gateway(8080)로 넘겨요. 같은 출처라 CORS가 필요 없어요.
  Vite 프록시는 개발 서버에서만 동작해요. 배포할 때는 같은 출처 구성(LB·nginx 경로 라우팅 또는 Vercel rewrites)이 필요해요.
- 로그아웃: OIDC 로그아웃(`/auth/connect/logout`)으로 auth 세션까지 끊어요.
