# 아키텍처

## 구성도 (로컬 docker-compose 기준)

```
브라우저 / 프론트
      │  http://localhost:8080  (호스트에 열린 유일한 서비스 포트)
      ▼
┌───────────┐  /auth/**  ─────────────►  auth   ──Feign──► user  (GET /user/api/v1/getUser)
│  gateway  │  /user/**  (JWT 필수) ───►  user   ──Feign──► auth  (POST /auth/api/v1/auth/signup, /delete)
└───────────┘  /order/** (JWT 필수) ───►  order  ──Feign──► warehouse (로컬에 없음)
                                            │
                                            └─Kafka─► 알림 토픽 (소비자는 이 레포 밖)
auth, user, order ── postgres(DB 3개, 계정 공유: D1) / auth ── mailpit(임시 비밀번호 메일)
```

- 서비스 안 포트는 모두 8080. auth, user, order는 호스트 포트가 없어 gateway를 거쳐야만 접근돼요(A3).
- 설정은 `infra/config/<서비스>.yml`을 `SPRING_CONFIG_LOCATION`으로 주입해요(운영의 ConfigMap 방식).

## 인증 흐름

1. 프론트가 `/auth/oauth2/authorize`로 이동 (Authorization Code + PKCE, `requireProofKey(true)`)
2. auth 로그인 화면(`/auth/login`, 이메일·비밀번호, 세션) → 인가 코드 발급
3. 프론트가 `/auth/oauth2/token`에 코드 + code_verifier를 보내 JWT(access token)를 받아요.
   JWT 클레임: `sub`(사용자 id), `name`, `rank`, `region`, `work_type` (`JwtCustomizerConfig`)
4. 프론트가 `Authorization: Bearer <JWT>`로 `/user/**`, `/order/**` 호출
5. gateway가 auth의 JWKS(`/auth/oauth2/jwks`)로 서명을 검증(`SecurityConfig`, 토큰 없으면 401)
6. gateway `JwtClaimsHeaderFilter`가 클라이언트가 보낸 `X-User-*`를 전부 지우고, 클레임으로 다시 채워요.
   `X-User-Id`(평문), `X-User-Name/Rank/Region/WorkType`(Base64)
7. user, order는 JWT를 다시 검증하지 않고 `X-User-*` 헤더만 믿어요.

## 신뢰 경계

| 경계 | 무엇을 믿나 | 지키는 곳 |
|---|---|---|
| 인터넷 → gateway | 아무것도 안 믿음. JWT 서명만 믿어요 | gateway `SecurityConfig` |
| gateway → user/order | `X-User-*` 헤더를 그대로 믿음 | 네트워크 격리(호스트 포트 없음), gateway의 헤더 삭제 |
| gateway → auth | `/auth/**`는 gateway가 통과시키고 auth 보안 설정이 직접 지켜요 | auth `SecurityConfig` (permitAll 목록 주의: C1~C3) |
| 서비스 ↔ 서비스 (Feign) | 인증 없음. 내부망이면 믿음 | 없음. auth의 signup/delete가 permitAll인 이유 |

## 서비스 간 호출

- auth → user: JWT를 발급할 때 클레임에 넣을 사용자 정보 조회(`JwtCustomizerConfig` → `UserClient`, `USER_SERVICE_URL`)
- user → auth: 사용자 생성·삭제 시 인증 계정 생성·삭제(`AuthClient`, `AUTH_SERVICE_URL`)
- order → warehouse: 출고·입고 요청, 출고 취소(`WarehouseClient`). 로컬에는 warehouse가 없어요.
- order → Kafka: 발주 상태 알림 발행
