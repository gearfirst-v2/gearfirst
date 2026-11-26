# 알려진 취약점

상태: `수정 완료(GEA-N)` / `재현됨` / `코드 확인`(코드로만 확인, 재현 전) / `미확인`
라벨은 바꾸지 않아요. 고치면 상태와 이슈 번호를 갱신해요.

| 라벨 | 서비스 | 공격 | 상태 |
|---|---|---|---|
| A1 | gateway | 토큰 없이 `X-User-*` 헤더를 위조해 권한 획득 | 수정 완료(GEA-8) |
| A2 | gateway | 토큰 없이 전체 사용자 목록 조회 | 수정 완료(GEA-8) |
| A3 | infra | 서비스 포트에 직접 접근해 gateway 우회 (로컬 compose에서만 확인, 운영은 미확인) | 수정 완료(GEA-8) |
| B1 | user | `registerUser`로 누구나 본사 팀장 계정 생성 | 코드 확인 |
| B2 | user | `updateUser`/`deleteUser`에 인가 검사 없음 | 코드 확인 |
| C1 | auth | `regenerate-temp-password`가 요청 body의 `personalEmail`로 임시 비밀번호 발송 → 계정 탈취 | 재현됨 |
| C2 | auth | `/api/v1/auth/delete`가 permitAll | 코드 확인 |
| C3 | auth | `change-password`가 body의 `userId`를 믿고, 무차별 대입 제한 없음 | 코드 확인 |
| C4 | auth | 로그인 폼 CSRF 비활성화 | 재현됨 |
| C5 | auth | 기본 계정 `test@gearfirst.com`/`1234`, client secret `"secret"` 하드코딩 | 코드 확인 |
| D1 | infra | auth, user, order가 postgres 계정 하나를 공유 | 코드 확인 |
| E1 | auth | JWK 서명 키와 인가 저장소가 메모리에만 있음(재시작하면 토큰 무효, 다중 인스턴스 불가) | 코드 확인 |
| E2 | gateway | 원본 JWT 헤더·페이로드를 ERROR 로그로 출력 | 재현됨 |
| (후보) | auth | `/api/v1/auth/signup`이 permitAll이고 gateway도 `/auth/**`를 열어 외부에서 직접 호출 가능할 수 있음 | 미확인 |

## 우선순위

1. C1 ~ C3 (계정 탈취·삭제)
2. B1 ~ B2 (권한 매트릭스 테스트 도입)
3. E1
4. D1
5. C4, C5, E2
