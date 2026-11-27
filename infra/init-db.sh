#!/bin/bash
# 서비스별 DB와 계정 (D1). 각 서비스는 자기 DB의 소유자 계정으로만 붙고, 다른 DB에는 접속할 수 없다.
# 데이터가 비어 있을 때(첫 실행)만 실행됨. 바꾸면 docker compose down -v 후 다시 up
set -euo pipefail
: "${AUTH_DB_PASSWORD:?}" "${USER_DB_PASSWORD:?}" "${ORDER_DB_PASSWORD:?}"

# 비밀번호는 SQL에 직접 붙이지 않고 psql 변수(:'pw')로 넘겨 따옴표 문제와 SQL 주입을 막는다
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -v auth_pw="$AUTH_DB_PASSWORD" -v user_pw="$USER_DB_PASSWORD" -v order_pw="$ORDER_DB_PASSWORD" <<'SQL'
-- LOGIN만 준다. SUPERUSER·CREATEDB·CREATEROLE은 기본값(없음)
CREATE ROLE auth_app LOGIN PASSWORD :'auth_pw';
CREATE ROLE user_app LOGIN PASSWORD :'user_pw';
CREATE ROLE order_app LOGIN PASSWORD :'order_pw';

-- 소유자라서 ddl-auto: update로 자기 테이블을 만들 수 있다
CREATE DATABASE authdb OWNER auth_app;
CREATE DATABASE userdb OWNER user_app;
CREATE DATABASE orderdb OWNER order_app;

-- 기본값은 모든 계정(PUBLIC)이 모든 DB에 접속 가능. 소유자만 남긴다
REVOKE CONNECT ON DATABASE authdb FROM PUBLIC;
REVOKE CONNECT ON DATABASE userdb FROM PUBLIC;
REVOKE CONNECT ON DATABASE orderdb FROM PUBLIC;
REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;
SQL
