# 0001. 담당 서비스 4개를 모노레포로 모으기

- 발견: 팀 원본은 서비스마다 레포가 따로 있고(Gear-First org), 실제 설정은 GitOps 레포의 ConfigMap에 있어요. 보안 수정은 gateway와 auth/user를 함께 바꾸는 경우가 많아 레포별 PR로는 한 변경을 한눈에 보여주기 어려워요.
- 선택지
  1. 원본 레포를 fork해서 레포별로 고치기
  2. 4개 서비스를 한 레포(gearfirst-v2/gearfirst)로 모으기
- 선택: 2. 공격 하나를 고치는 변경(예: gateway 헤더 삭제 + 서비스 포트 제거)을 PR 하나로 보여줄 수 있어요. 로컬 실행(compose)과 CI도 한 곳에서 관리해요.
- 포기한 것
  - 원본 레포와의 자동 동기화. 이후 원본 변경은 따라가지 않아요.
  - 루트 빌드. 서비스마다 Gradle 프로젝트를 그대로 두고 폴더별로 빌드해요(CI는 바뀐 서비스만).
  - gateway 히스토리. 원본이 private이라 히스토리 없이 가져왔고, `gateway.p12`는 제외했어요. auth/user/order는 subtree로 원본 커밋 SHA를 유지해요(그래서 히스토리 수정 금지).
- 증명: GEA-8에서 gateway·infra를 함께 고친 PR 하나로 A1~A3를 닫았어요.
