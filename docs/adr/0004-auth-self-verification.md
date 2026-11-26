# 0004. 인증은 gateway에서 하고, auth만 직접 토큰을 검증하기

- 발견: GEA-13(C2·C3)을 고치려면 auth의 signup, delete, change-password에서 호출자를 확인해야 했어요([C2-C3-auth-caller-check.md](../security/C2-C3-auth-caller-check.md)). 지금 인증은 gateway만 해요(JWT 검증 후 `X-User-*` 주입, user·order는 헤더를 믿음). signup·delete에 고른 client credentials(③)는 받는 쪽인 auth가 토큰을 검증해야 의미가 있어요. 그래서 "검증은 gateway가 하는 것 아닌가?"부터 다시 봤어요.

## 선택지: 인증 배치 패턴

| 패턴 | 구조 | 주로 쓰는 곳 |
|---|---|---|
| 1. gateway만 검증 (지금 user, order) | gateway가 검증하고 헤더를 넣어요. 서비스는 헤더를 믿어요 | 작은 팀, 내부망이 확실히 닫혀 있을 때 |
| 2. gateway + 서비스 둘 다 | 같은 JWT를 양쪽에서 검증해요 | Spring Cloud 구성에서 흔해요. 설정만으로 돼요 |
| 3. gateway가 내부용 토큰으로 바꿔 줌 | 외부 토큰은 gateway에서 검증해요. 서비스에는 짧게 만료되는 내부 전용 토큰을 넘겨요 | 큰 조직. 외부 토큰이 내부로 퍼지지 않게 할 때 |
| 4. 서비스 메시(Istio 등) | 서비스 옆의 sidecar가 mTLS와 JWT 검증을 해요. 앱 코드에는 인증이 없어요 | Kubernetes를 깊게 쓰는 조직 |

### auth에 검증을 넣는다면

auth의 API는 두 종류로 나눠서 봐야 해요.

- 열려 있어야 하는 것: 로그인, `/oauth2/token`, JWKS(공개키). 토큰을 받기 전에 쓰는 곳이라 모든 패턴에서 permitAll이에요.
- 지켜야 하는 것: change-password(사용자용), signup과 delete(내부용).

| 패턴 | change-password (본인 확인) | signup, delete (내부 호출 확인) |
|---|---|---|
| 1. gateway만 검증 | gateway가 이 경로만 토큰 필수로 하고 `X-User-Id`를 넣어요. auth는 헤더를 믿어요 (A-2) | gateway에서 외부 접근을 차단하고(①), NetworkPolicy로 user만 부를 수 있게 해요 |
| 2. 둘 다 검증 | auth가 resource server로 JWT를 직접 검증해요 (A-1) | client credentials 토큰의 scope를 검사해요 (③) |
| 3. 내부 토큰으로 교환 | gateway가 외부 토큰을 auth에 보내 내부 토큰으로 바꿔요(token exchange). auth의 API는 내부 토큰만 받아요. auth가 내부 토큰 발급자라 역할이 커져요 | 서비스도 auth에서 내부 토큰을 받아요 (③과 비슷해요) |
| 4. 서비스 메시 | sidecar가 auth의 JWKS로 JWT를 검증해요. auth 코드는 검증된 신원만 읽어요 | sidecar 정책으로 "user 서비스 계정만 호출 가능"을 정해요. mTLS 인증서가 ③을 대신해요 |

## 선택: 전체는 패턴 1, auth만 예외로 직접 토큰을 검증해요

> 외부 요청은 gateway가, auth로 오는 요청은 auth가 검증해요. 나머지 내부 호출은 내부망을 신뢰해요.

- change-password: auth가 JWT를 검증하고 `sub`로 본인을 확인해요. 5회 실패하면 15분 잠가요.
- signup, delete: client credentials 서비스 토큰의 scope `internal`을 확인해요.
- user, order는 지금처럼 gateway 헤더를 믿어요. GEA-8에서 서비스 포트를 닫아 gateway를 거쳐야만 들어와요.

### auth만 예외인 이유

- gateway는 로그인과 토큰 발급 때문에 `/auth/**`를 검증 없이 통과시켜요.
- user의 Feign 호출은 gateway를 지나지 않아요.
- 운영 ingress가 gateway를 우회할 수 있어요(원본 운영은 ingress가 `/auth`를 auth로 직접 보냄, 미확인). 그래서 auth가 헤더를 믿으면 위조될 수 있어요.
- gateway `SecurityConfig` 주석에 이미 "auth 안의 보안 설정이 각 엔드포인트를 직접 지킨다"는 약속이 있어요. 새 예외가 아니라 지켜지지 않던 약속을 지키게 하는 거예요.
- ③ 때문에 auth에 검증 설정이 들어가고, change-password도 같은 설정을 재사용해요.
- auth는 토큰을 발급하는 곳이고 gateway가 원래 검증하지 않던 경로라, 같은 요청을 두 번 검증하는 중복도 생기지 않아요.

## 참고: gateway의 역할

- 단일 입구·라우팅: 외부에는 주소 하나만 보여요. 내부 구조를 숨겨요.
- 1차 인증: 토큰이 없거나 가짜인 요청을 서비스에 닿기 전에 버려요.
- 헤더 정리: 외부에서 온 `X-User-*`를 지우고 검증된 토큰으로 다시 채워요(GEA-8).
- 공통 처리: CORS, 요청 횟수 제한, TLS 종료, 접근 로그, 추적 ID.

gateway는 "들어와도 되는 사람인가"를 거칠게 걸러요. "이 사람이 이 일을 해도 되나"는 데이터를 가진 서비스만 판단할 수 있어요.

### 패턴 2: 중복 검증인데도 gateway에서 검증하는 이유

gateway와 서비스가 둘 다 검증하면 같은 일을 두 번 하는 중복이에요. 그래도 gateway에서 검증하는 이유는 이래요.

- 일찍 버리기: 가짜 토큰과 공격 트래픽이 서비스까지 가지 않아요. 서비스 부하와 로그가 줄어요.
- gateway 자체 기능에 사용자가 필요해서: 사용자별 요청 횟수 제한, 사용자 ID가 찍힌 접근 로그는 gateway가 토큰을 읽어야 가능해요.
- 헤더 정리: 외부에서 위조한 `X-User-*`를 지우고 검증된 값으로 다시 넣어요.

서비스에서도 검증하는 이유는 gateway를 지나지 않는 길(ingress 설정 실수, 내부 호출, Pod 탈취)에 대비한 두 번째 잠금이에요. JWT 검증은 캐시한 공개키로 서명만 계산해서 비용이 작아요.

## 참고: 패턴 1에서 내부망을 닫는 방법

아래로 갈수록 강해요.

| 단계 | 방법 | 이 프로젝트 |
|---|---|---|
| 포트 공개 안 함 | 서비스 포트를 밖으로 열지 않고 gateway만 열어요 | 로컬은 GEA-8에서 했어요 |
| 네트워크 분리 | compose에서 `internal: true` 네트워크를 두고, gateway만 바깥과 안쪽 양쪽에 연결해요 | 아직 안 했어요 |
| K8s Service + NetworkPolicy | Service는 ClusterIP(내부 주소만)로 둬요. "기본 전부 차단, gateway → user·order·auth, user → auth, auth → user만 허용"처럼 누가 누구를 부를지 정해요 | GCP 배포 때 |
| 클라우드 방화벽 / private cluster | 노드에 공인 IP를 주지 않아요. 외부에서는 로드밸런서 → gateway만 열어요 | GCP 배포 때 |
| mTLS(서비스 메시) | 같은 네트워크 안이어도 허용된 서비스의 인증서가 없으면 연결을 거부해요 | 과할 수 있어요 |

- 위 네 단계는 "어디서 왔나"(네트워크 위치)로 막아요. mTLS는 "누구인가"(신원)로 막아요.
- "확실히"는 없어요. 허용된 Pod가 털리거나 gateway에 SSRF가 있으면 허용된 경로로 정상처럼 호출돼요.
- 운영의 가장 큰 구멍은 ingress가 `/auth`를 gateway 없이 auth로 보내는 것이에요(주석 기준, 미확인).

## 이번 선택의 한계

- 내부망 신뢰: user와 order는 gateway 헤더를 믿어요. gateway를 우회하는 경로가 생기면 헤더 위조가 가능해요.
- auth → user 호출은 검증이 없어요: 토큰 생성 때 auth가 user를 부르는 호출(`UserClient.getUser`)은 내부망 신뢰에 기대요. B1·B2 때 다시 봐요.
- 운영 ingress 미확인: `/auth`가 gateway를 우회하는지 GCP 배포 때 확인해야 해요.
- 서명 키가 메모리에만 있어요(E1): auth가 재시작하면 서비스 토큰이 만료될 때까지(최대 5분) 직원 등록·삭제가 실패해요.
- `regenerate-temp-password`는 로그인 전에 쓰는 API라 permitAll로 남겨요. 남의 비밀번호를 바꿔 버리는 문제는 C6에서 고쳐요.
- 프론트: 원본(Gear-First/Web-FE)을 기반으로 나중에 새로 만들어요. 새 프론트는 change-password에 Bearer 토큰만 보내고 `userId`는 빼요. auth signup은 부르지 않아요(직원 등록은 user `registerUser`). 자세한 규칙은 [C2-C3-auth-caller-check.md](../security/C2-C3-auth-caller-check.md) 한계에 있어요.
- 비밀번호 대입은 change-password에서만 막아요. 동시 요청 우회(C7)와 로그인 폼 대입(C8)은 따로 고쳐요.
- 범위 밖: 하드코딩된 client secret(C5).

## auth와 user를 나눈 구조의 한계와 해결방안

이 한계는 이번 선택 때문이 아니라 원래 구조에서 와요. 지금은 구조를 바꾸지 않고 기록만 해요.

### 1. user가 죽으면 로그인할 수 없어요

auth가 토큰을 만들 때마다 user를 동기로 불러요. 기본 해결 방향은 로그인 경로에서 다른 서비스를 동기로 부르지 않는 거예요.

| 방법 | 내용 | 크기 |
|---|---|---|
| 타임아웃 + 서킷 브레이커 | user 호출을 1~2초로 끊고, 계속 실패하면 잠시 멈춰요(Resilience4j). 지금 `UserClient`는 타임아웃이 없어 기본값(읽기 60초)을 써요 | 작음. 장애는 그대로지만 빨리 실패해요 |
| auth에 필요한 정보만 복제 | 직급, 이름처럼 토큰에 넣을 것만 auth DB에도 둬요. 이벤트로 동기화해요 | 중간. 로그인이 user와 무관해져요 |
| 권한 정보는 인증 서버가 가짐 | 역할(role)은 인증 서버가 갖고, 업무 정보만 user에 둬요 | 큼. 데이터 소유권이 바뀌어요 |

### 2. 두 DB가 어긋날 수 있어요

직원 등록이 user DB와 auth DB 두 곳에 저장돼요. 중간에 실패하면 한쪽에만 남을 수 있어요.

| 방법 | 내용 |
|---|---|
| Outbox + 이벤트 | user가 사람 저장과 가입 이벤트 기록을 같은 트랜잭션에 넣어요. 발행기가 Kafka로 보내고 auth가 받아 처리해요. 중복 수신에도 한 번만 처리해요(멱등) |
| Saga(보상) | auth 저장에 실패하면 user에 되돌리기 이벤트를 보내요. 또는 `PENDING → ACTIVE / FAILED` 상태로 관리해요 |
| 순서 뒤집기 | 인증 서버가 먼저 계정을 만들고, 그 ID(`sub`)로 user 프로필을 만들어요 |

### 3. 서로를 부르는 순환 의존

user → auth(signup, delete), auth → user(토큰 생성 시 조회). ③을 하면 user가 auth의 토큰 발급에도 의존해서 조금 더 늘어나요.

- user → auth를 이벤트로 바꾸면 signup·delete Feign이 사라져요. 그 경로에서는 ③도 필요 없어져요.
- auth → user를 복제로 바꾸면 로그인 시 조회가 사라져요.
- 둘 다 하면 서로 직접 부르는 호출이 없어져요.

## 증명

[C2-C3-auth-caller-check.md](../security/C2-C3-auth-caller-check.md)의 증명(토큰 없음 401, 사용자 토큰 403, 서비스 토큰 200, change-password 본인 확인)으로 대신해요.
