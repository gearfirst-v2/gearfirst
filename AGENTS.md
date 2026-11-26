# AGENTS.md

GearFirst v2: 부트캠프 팀 MSA 프로젝트 중 auth, user, order, gateway만 가져와 인증/인가 보안을 고도화하는 모노레포.
범위는 이 4개 서비스뿐이에요. warehouse, repair, notification 등 다른 서비스는 이 레포에 없어요.

## 구조

- `auth/` 인가 서버(Spring Authorization Server, OAuth2 + PKCE, JWT 발급)
- `user/` 사용자·조직, `order/` 발주, `gateway/` Spring Cloud Gateway(WebFlux, JWT 검증 후 `X-User-*` 헤더 주입)
- `infra/` 로컬 실행용 docker-compose, 설정 `infra/config/*.yml`, 비밀값 `infra/.env`

## 빌드·테스트

- 전체: 루트에서 `./gradlew build` (컴파일 + 테스트 + jar). 서비스 하나만: 그 폴더에서 `./gradlew build`. Gradle은 모두 9.1.0이에요.
- Java 21이 필요해요. 로컬 기본 Java는 26이에요. `JAVA_HOME`이 21이 아니면 명령 앞에 `JAVA_HOME=$(/usr/libexec/java_home -v 21)`을 붙여요.
- 커밋 전 `./gradlew spotlessApply`로 포맷을 맞춰요(palantir-java-format). 포맷이 틀리면 CI의 `spotlessCheck`가 실패해요.
- 테스트 하나만(서비스 폴더에서): `./gradlew test --tests 'com.gearfirst.gateway.HeaderSpoofingTest'`
- 테스트는 H2를 써요(DB 없이 실행). 설정은 서비스별 `src/test/resources/application.yml`.
- 로컬 실행: `cd infra && docker compose up -d --build`. 외부 진입점은 gateway(8080) 하나, 메일은 Mailpit(8025).

## 금지

- Spring Boot 버전 임의 변경 금지(gateway 3.5.7, 나머지 3.4.2). 바꿔야 할 것 같으면 이유와 함께 제안만 하고, 사용자가 승인하면 ADR을 쓰고 바꿔요.
- 다른 사람 커밋이 있는 히스토리 수정 금지(rebase, amend, force push). auth/user/order는 원본 커밋 SHA를 유지해요.
- `.env` 커밋 금지. `infra/.env`는 요청받아도 읽거나 출력하지 않아요.

## 작업 흐름

1. (사용자) Linear 이슈와 브랜치를 만들어요.
2. (에이전트) 사용자가 알려준 브랜치에서만 작업해요. 사용자가 만들어달라고 하지 않는 한 이슈·브랜치를 직접 만들지 않아요. 브랜치를 안 알려주면 현재 브랜치를 보여주고 어디서 할지 물어요.
3. (에이전트) 보안·버그 수정이면 재현 테스트를 먼저 쓰고, 실패하는 걸 확인해요.
4. (에이전트) 수정해요. 테스트 없는 코드를 고칠 땐 기존 동작을 고정하는 테스트부터 써요.
5. (에이전트) 바뀐 서비스의 `./gradlew build`가 통과하는지 확인해요.
6. (에이전트) 작업 단위가 끝나면(build 통과) 커밋 나누기안을 먼저 보여줘요: 커밋별 파일 목록, 제목, 본문.
7. (에이전트) 이슈의 할 일이 모두 끝나면 PR 제목과 본문도 먼저 써서 보여줘요.
8. (에이전트) 커밋·push·PR은 사용자가 요청할 때만 해요.

- 보안 수정 테스트에는 정상 요청과 함께 권한 없는 사용자, 다른 사람 리소스, 잘못된 값·빈 값을 넣어요.
- 기존 테스트가 깨지면 새 작업을 멈추고 회귀인지 의도된 동작 변경인지 먼저 판단해요. 회귀면 코드를 고치고, 의도된 변경이면 이유를 설명하고 기대값 변경을 확인받아요. 테스트를 지우거나 건너뛰게 바꾸지 않아요.

- type: `feat` 기능, `fix` 보안·버그 수정, `refactor` 동작 변경 없는 구조 개선, `test`, `ci`, `docs`, `chore` 설정·정리
- 브랜치: `type/gea-N-설명` 예) `fix/gea-8-header-spoofing`
- PR 제목: `type(scope): 설명 (GEA-N)` 예) `fix(gateway): X-User-* 헤더 위조 차단 (GEA-8)`. 커밋 메시지도 같은 형식을 써요.
- scope는 바꾼 폴더(auth, user, order, gateway, infra). 여러 개면 쉼표로, 레포 전체면 생략해요.
- PR 본문: `.github/pull_request_template.md` 형식
- dev에는 PR 없이 push할 수 있어요. main에는 PR로만 들어가고 `ci-result`가 통과해야 머지돼요.
- 공격 라벨(A1, C1 등)은 바꾸지 말고 그대로 써요. 목록은 `docs/known-issues.md`. 라벨에 해당하는 이슈 번호(GEA-N)는 사용자에게 물어요.

## 협업 규칙

- 방법이 여러 개면 선택지와 장단점을 보여주고, 결정은 사용자가 해요.
- 실행 단계에서 "같이 해보자"는 명령어를 알려달라는 뜻이에요. 대신 실행하지 마세요. 설계 이야기에서는 선택지와 장단점을 보여줘요.
- 실행 단계(git 커밋·삭제·브랜치 변경, docker 실행, Linear·GitHub 같은 외부 작업)는 하기 전에 누가 할지(사용자/에이전트) 확인해요. "~해줘"로 직접 요청하면 에이전트가 해요. "~해줘"는 그 문장이 직접 가리키는 일에만 적용되고, 작업 중에 따로 필요해진 실행 단계는 다시 물어요.
- 읽기만 하는 명령(git status·branch·log·diff, docker compose ps·logs)은 확인 없이 해요.
- 파일·코드 작성은 확인 없이 하되, 만들 때마다 왜 그렇게 하는지 짧게 설명해요.

## 문서 규칙

- 취약점을 고치면 `docs/known-issues.md`의 상태를 갱신해요.
- 결정은 발견 → 선택지 → 선택 이유 → 포기한 것 → 증명(테스트·재현 결과) 순서로 기록해요. 여러 서비스에 영향을 주거나, 되돌리기 어렵거나, 선택지를 두고 고민한 결정은 `docs/adr/`에 새 파일로.
- 진행 기록(Notion)은 사용자가 직접 써요.

## 언제 어떤 문서를 읽나

필요할 때만 읽어요. 미리 다 읽지 마세요.

- 서비스 간 호출·인증 흐름을 건드릴 때: `docs/architecture.md`
- 취약점을 고치거나 공격 라벨을 쓸 때: `docs/known-issues.md`
- 왜 이런 구조인지 궁금할 때(모노레포, 로컬 실행 방식): `docs/adr/`
