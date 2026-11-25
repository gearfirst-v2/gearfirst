# 0002. 로컬 실행 방식 (infra/docker-compose.yml)

- 발견: 원본은 GKE + ConfigMap + 쿠버네티스 서비스 디스커버리(`lb://user-service`)로 돌아가서, 로컬에서 4개 서비스를 함께 띄워 공격을 재현할 방법이 없었어요.
- 선택지: (1) 로컬 k8s(kind 등) (2) docker-compose (3) IDE에서 서비스별 직접 실행
- 선택: docker-compose. 명령 하나로 postgres, kafka, mailpit, 4개 서비스를 띄우고, 운영과 같은 방식으로 설정 파일을 주입할 수 있어요.

## compose 안의 결정들

| 결정 | 이유 | 포기한 것 |
|---|---|---|
| 설정은 `infra/config/*.yml`을 마운트하고 `SPRING_CONFIG_LOCATION`으로 지정 | 운영의 ConfigMap 마운트 방식과 같게 | 서비스 레포 안 `application.yml` 사용 |
| 비밀값은 `infra/.env`(커밋 안 함)의 `POSTGRES_PASSWORD` | 비밀번호를 레포에 남기지 않기 | - |
| auth, user, order는 호스트 포트를 열지 않음 | 운영(ClusterIP)처럼 gateway를 거쳐야만 접근 → gateway 검사 우회 불가(A3) | IDE·curl로 서비스에 직접 붙는 편의 |
| 외부 진입점은 gateway 8080 하나 | 브라우저·프론트가 주소 하나만 알면 됨 | 원본의 8443(HTTPS): 로컬엔 TLS 설정이 없어요 |
| gateway에 `/auth/**` 라우트 추가 | 원본은 ingress가 `/auth`를 auth로 직접 보냈는데 로컬엔 ingress가 없어요 | - |
| `AUTH_ISSUER=http://localhost:8080/auth`, `PreserveHostHeader` | 브라우저가 보는 주소 기준으로 issuer·리다이렉트가 만들어지게 | - |
| Kafka 호스트 포트는 29092만 | 9092는 다른 프로젝트가 사용 중. 컨테이너끼리는 `kafka:9092` | - |
| Mailpit(웹 8025) | 임시 비밀번호 메일을 실제 발송 없이 확인(C1 재현에 사용) | - |
| postgres 계정 하나를 서비스 3개가 공유 | 보안 수정 전후(Before/After) 증거를 남기려고 일부러 유지(D1) | 서비스별 최소 권한 계정(D1 수정 때 도입) |
| `init-db.sql`은 데이터가 빈 첫 실행에만 실행 | postgres 이미지 동작. 바꾸면 `docker compose down -v` 후 다시 `up` | - |
| `WAREHOUSE_URL`은 존재하지 않는 서비스를 가리킴 | warehouse는 범위 밖 | order의 재고 연동 기능 |

- 증명: GEA-5에서 로컬 실행, GEA-8에서 서비스 포트 제거로 A3 재현 → 차단 확인.
