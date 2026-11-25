# 0003. 루트 빌드 추가와 Gradle 버전 통일

- 발견: 서비스 4개가 독립 Gradle 프로젝트라 전체 빌드는 폴더마다 4번 해야 했어요. 루트로 묶으려 보니 Gradle 버전이 달랐어요(auth·order 9.1.0, user·gateway 8.14.3). `includeBuild`로 묶으면 루트 Gradle 버전 하나로 4개를 빌드해요.
- 확인: 서로 버전을 바꿔 빌드해 보니 4개 모두 9.1.0과 8.14.3 둘 다에서 빌드됐어요.
- 선택지
  1. 루트 wrapper만 9.1.0, 서비스 wrapper는 그대로
  2. 루트 wrapper 9.1.0 + user·gateway wrapper도 9.1.0으로 통일
  3. Gradle 대신 스크립트로 폴더마다 `./gradlew build`
- 선택: 2. 루트에서든 폴더에서든(CI 포함) 같은 Gradle로 빌드돼요. 바뀌는 건 wrapper 버전 줄 두 개뿐이에요.
- 포기한 것
  - 1: 같은 서비스가 로컬 루트 빌드와 CI(폴더 빌드)에서 다른 Gradle로 돌아 결과가 어긋날 수 있어요.
  - 3: 버전 문제는 없지만, 포맷 적용 같은 태스크를 루트에서 한 번에 돌릴 수 없어요.
  - Spring Boot 버전은 그대로예요(gateway 3.5.7, 나머지 3.4.2). `includeBuild`는 서비스를 독립 빌드로 두어서 Boot 버전이 달라도 돼요.
- 구현: 루트 `settings.gradle`(includeBuild 4개), `build.gradle`(build·test·clean을 4개 서비스로 전달), auth의 wrapper 복사.
- 증명: 루트 `./gradlew clean build` 통과(4개 서비스 test·build 실행). user·gateway 폴더 빌드도 Gradle 9.1.0으로 통과.
