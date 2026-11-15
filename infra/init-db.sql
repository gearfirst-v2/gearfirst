-- 서비스별 DB. 지금은 원래 구조처럼 postgres 계정 하나를 같이 쓴다.
-- 3단계(보안 수정)에서 서비스별 계정 분리로 바꿀 예정.
CREATE DATABASE authdb;
CREATE DATABASE userdb;
CREATE DATABASE orderdb;
