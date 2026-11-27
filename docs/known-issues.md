# 알려진 취약점

상태: `수정 완료(GEA-N)` / `재현됨` / `코드 확인`(코드로만 확인, 재현 전) / `미확인`
라벨은 바꾸지 않아요. 고치면 상태와 이슈 번호를 갱신해요.

| 라벨 | 서비스 | 공격 | 상태 |
|---|---|---|---|
| A1 | gateway | 토큰 없이 `X-User-*` 헤더를 위조해 권한 획득 | 수정 완료(GEA-8) |
| A2 | gateway | 토큰 없이 전체 사용자 목록 조회 | 수정 완료(GEA-8) |
| A3 | infra | 서비스 포트에 직접 접근해 gateway 우회 (로컬 compose에서만 확인, 운영은 미확인) | 수정 완료(GEA-8) |
| B1 | user | `registerUser`로 누구나 본사 팀장 계정 생성 | 수정 완료(GEA-15) |
| B2 | user | `updateUser`/`deleteUser`에 인가 검사 없음 | 수정 완료(GEA-15) |
| C1 | auth | `regenerate-temp-password`가 요청 body의 `personalEmail`로 임시 비밀번호 발송 → 계정 탈취 | 수정 완료(GEA-12) |
| C2 | auth | `/api/v1/auth/delete`가 permitAll (같은 원인으로 `signup`도 열려 있어 delete와 엮으면 계정 탈취) | 수정 완료(GEA-13) |
| C3 | auth | `change-password`가 body의 `userId`를 믿고, 무차별 대입 제한 없음 | 수정 완료(GEA-13) |
| C4 | auth | 로그인 폼 CSRF 비활성화. 지금은 auth CORS가 다른 출처의 로그인 POST를 우연히 막고 있어서, CSRF를 켠 뒤 auth CORS를 지우거나 API 경로로 좁혀야 함(GEA-14) | 재현됨 |
| C5 | auth | 기본 계정 `test@gearfirst.com`/`1234`, client secret `"secret"` 하드코딩 | 코드 확인 |
| C6 | auth | `regenerate-temp-password` 요청만으로 남의 비밀번호가 즉시 바뀜 → 계정 잠금, 메일 폭탄 (C1 한계, `docs/security/C1-temp-password.md`) | 코드 확인 |
| C7 | auth | `change-password` 잠금의 실패 횟수를 동시 요청이 서로 덮어써서 5번보다 많이 대입 가능(그 사람의 토큰 필요, GEA-13 한계) | 코드 확인 |
| C8 | auth | 로그인 폼에 시도 제한이 없어 비밀번호 무차별 대입 가능. BCrypt 계산이 몰리면 정상 로그인도 느려짐 | 코드 확인 |
| D1 | infra | auth, user, order가 postgres 슈퍼유저 계정 하나를 공유 → 한 서비스 탈취로 다른 DB 변조·OS 명령 실행 | 수정 완료(GEA-17) |
| D2 | infra | postgres·mailpit·kafka 호스트 포트가 모든 인터페이스(`0.0.0.0`, `[::]`)에 열려 같은 네트워크에서 DB 무차별 대입, 임시 비밀번호 메일 열람, 이벤트 주입 가능 | 수정 완료(GEA-17) |
| E1 | auth | JWK 서명 키와 인가 저장소가 메모리에만 있음(재시작하면 토큰 무효, 다중 인스턴스 불가) | 코드 확인 |
| E2 | gateway | 원본 JWT 헤더·페이로드를 ERROR 로그로 출력 | 재현됨 |
| (후보) | auth | 비밀번호를 바꿔도 이미 발급한 토큰·auth 세션이 폐기되지 않아, 탈취된 access 토큰을 최대 30분, refresh 토큰을 최대 1일 더 쓸 수 있음 | 코드 확인 |
| (후보) | auth | `/api/v1/auth/signup`이 permitAll이고 gateway도 `/auth/**`를 열어 외부에서 직접 호출 가능할 수 있음 | 미확인 |

## 보안 외 문제

공격은 아니지만 보안 수정 중 발견한 문제예요. 공격 라벨은 붙이지 않아요.

| 서비스 | 문제 | 관련 |
|---|---|---|
| gateway, auth | 브라우저 로그인 불가: gateway의 Spring Security 기본 `Referrer-Policy: no-referrer` 때문에 로그인 폼 POST의 Origin이 `null`이 되고, auth CORS가 403으로 거부. gateway를 `same-origin`으로 바꿔 수정(GEA-14). 근본 원인(auth CORS가 로그인 폼에도 걸림)은 C4에서 정리 | C4, `gateway/.../ReferrerPolicyTest.java` |
| auth | 기본 계정 `test@gearfirst.com`은 auth에만 있고 user에 없어서(`userId` 없음) 토큰 발급 때 user 조회가 400 → 500으로 로그인 불가. 로컬 확인은 user `registerUser`로 만든 계정을 씀 | C5 |
| auth | 가입 때 임시 비밀번호 메일 발송이 실패해도 로그만 남고 가입은 성공 처리. 인사 담당자는 실패를 알 수 없음 | C6(인사 담당자 다시 보내기와 같이 설계), `docs/security/C1-temp-password.md` |

## 우선순위

1. ~~C1 ~ C3 (계정 탈취·삭제)~~ 완료
2. ~~B1 ~ B2 (권한 매트릭스 테스트 도입)~~ 완료
3. D1
4. C4, C5, C6
5. 모니터링
6. k6 기준선
7. 성능 사례: E1, E2, N+1, C7, C8 (k6로 측정하며 고침, GCP 배포 전 필수)
