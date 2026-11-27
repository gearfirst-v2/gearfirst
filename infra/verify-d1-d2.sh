#!/usr/bin/env bash
# D1·D2 검증: 떠 있는 compose를 대상으로 DB 계정 격리와 호스트 포트 노출을 검사한다.
# 사용: cd infra && ./verify-d1-d2.sh   (하나라도 FAIL이면 종료 코드 1)
# 비밀번호는 컨테이너 환경변수에서 읽어 쓰기만 하고 화면에 출력하지 않는다.
set -u
cd "$(dirname "$0")"

NETWORK=gearfirst_default
SERVICES=(auth user order)
DBS=(authdb userdb orderdb postgres)
pass=0 fail=0 skip=0

ok()   { echo "PASS $1"; pass=$((pass + 1)); }
bad()  { echo "FAIL $1"; fail=$((fail + 1)); }
skp()  { echo "SKIP $1"; skip=$((skip + 1)); }

# 다른 컨테이너에서 네트워크로 접속한다. postgres 컨테이너 안의 psql은 trust라 비밀번호 검사가 안 된다.
psql_as() { # user password db sql → stdout+stderr
  docker run --rm --network "$NETWORK" -e PGPASSWORD="$2" postgres:16 \
    psql -h postgres -U "$1" -d "$3" -tAc "$4" 2>&1
}
db_password() { docker compose exec -T "$1" printenv DB_PASSWORD 2>/dev/null; }

# AC1·AC2·AC3·AC4·AC5·AC6·AC7: 서비스별 계정
for svc in "${SERVICES[@]}"; do
  user="${svc}_app" own="${svc}db" pw="$(db_password "$svc")"
  if [ -z "$pw" ]; then bad "AC1-7 $svc: DB_PASSWORD를 읽을 수 없음"; continue; fi

  for db in "${DBS[@]}"; do
    [ "$db" = "$own" ] && continue
    out="$(psql_as "$user" "$pw" "$db" 'select 1')"
    if grep -q 'permission denied for database' <<<"$out"; then ok "AC1-3 $user → $db 거부"
    else bad "AC1-3 $user → $db 거부되지 않음: $(head -1 <<<"$out")"; fi
  done

  out="$(psql_as "$user" "$pw" "$own" "select count(*) from pg_tables where schemaname='public' and tableowner=current_user")"
  if [[ "$out" =~ ^[1-9][0-9]*$ ]]; then ok "AC4 $user → $own 접속, 자기 테이블 $out개"
  else bad "AC4 $user → $own 자기 테이블 없음 또는 접속 실패: $(head -1 <<<"$out")"; fi

  out="$(psql_as "$user" "$pw" "$own" 'select rolsuper or rolcreaterole or rolcreatedb from pg_roles where rolname = current_user')"
  if [ "$out" = "f" ]; then ok "AC5 $user 슈퍼유저·CREATEROLE·CREATEDB 아님"
  else bad "AC5 $user 권한 과다 또는 확인 실패: $(head -1 <<<"$out")"; fi

  out="$(psql_as "$user" "$pw" "$own" "COPY (select 1) TO PROGRAM 'true'")"
  if grep -q 'ERROR:.*permission denied' <<<"$out"; then ok "AC6 $user COPY TO PROGRAM 거부"
  else bad "AC6 $user COPY TO PROGRAM 실행됨 또는 확인 실패: $(head -1 <<<"$out")"; fi

  # 맞는 비밀번호로 접속이 돼야 의미가 있다. 계정이 없어도 같은 에러가 나기 때문
  if [ "$(psql_as "$user" "$pw" "$own" 'select 1')" != 1 ]; then bad "AC7 $user 맞는 비밀번호로도 접속 안 됨"
  elif grep -q 'password authentication failed' <<<"$(psql_as "$user" "wrong-$RANDOM" "$own" 'select 1')"; then ok "AC7 $user 틀린 비밀번호 거부"
  else bad "AC7 $user 틀린 비밀번호가 거부되지 않음"; fi
done

# AC8: DB 소유자와 PUBLIC CONNECT 권한
pw="$(db_password auth)"
rows="$(psql_as auth_app "$pw" authdb "select datname||'|'||pg_get_userbyid(datdba)||'|'||coalesce(datacl::text,'NULL') from pg_database where datname in ('authdb','userdb','orderdb','postgres')")"
for db in "${DBS[@]}"; do
  row="$(grep "^$db|" <<<"$rows")"
  if [ -z "$row" ]; then bad "AC8 $db 조회 실패: $(head -1 <<<"$rows")"; continue; fi
  owner="$(cut -d'|' -f2 <<<"$row")" acl="$(cut -d'|' -f3 <<<"$row")"
  expected="${db%db}_app"; [ "$db" = postgres ] && expected=postgres
  if [ "$owner" = "$expected" ]; then ok "AC8 $db 소유자 $owner"; else bad "AC8 $db 소유자 '$owner' (기대 $expected)"; fi
  # acl이 NULL이면 기본값 = PUBLIC CONNECT 허용. PUBLIC 항목은 '=권한/부여자' 형태
  if [ "$acl" != "NULL" ] && ! [[ "$acl" =~ [{,]=[A-Za-z]*c ]]; then ok "AC8 $db PUBLIC CONNECT 없음"
  else bad "AC8 $db PUBLIC CONNECT 허용 ($acl)"; fi
done

# AC9: 개발용 호스트 포트는 127.0.0.1에만, gateway는 그대로
for svc in postgres mailpit kafka; do
  ports="$(docker compose ps --format '{{.Ports}}' "$svc")"
  if [[ "$ports" == *127.0.0.1:* && "$ports" != *0.0.0.0:* && "$ports" != *"[::]:"* ]]; then ok "AC9 $svc 포트 127.0.0.1만"
  else bad "AC9 $svc 포트 노출: $ports"; fi
done
ports="$(docker compose ps --format '{{.Ports}}' gateway)"
if [[ "$ports" == *0.0.0.0:8080* ]]; then ok "AC9 gateway 8080은 열려 있음"; else bad "AC9 gateway 8080 닫힘: $ports"; fi

# AC10·AC11: 와이파이 주소로는 거부, localhost로는 접속
wifi="$(ipconfig getifaddr en0 2>/dev/null)"
for port in 5432 8025 1025 29092; do
  if [ -z "$wifi" ]; then skp "AC10 $port 와이파이 IP 없음(en0)"
  elif nc -z -G 2 "$wifi" "$port" 2>/dev/null; then bad "AC10 와이파이 IP로 $port 접속됨"
  else ok "AC10 와이파이 IP로 $port 거부"; fi
done
for port in 5432 8025; do
  if nc -z -G 2 127.0.0.1 "$port" 2>/dev/null; then ok "AC11 localhost:$port 접속"
  else bad "AC11 localhost:$port 접속 안 됨"; fi
done

# AC12: 서비스에 슈퍼유저 비밀번호 없음, 서비스별 비밀번호가 서로 다름(값은 해시로만 비교)
fingerprint() { shasum | cut -c1-12; }
super="$(docker compose exec -T postgres printenv POSTGRES_PASSWORD 2>/dev/null | fingerprint)"
hashes=()
for svc in "${SERVICES[@]}"; do
  h="$(db_password "$svc" | fingerprint)"
  if [ "$h" = "$super" ]; then bad "AC12 $svc가 슈퍼유저 비밀번호를 받음"
  else ok "AC12 $svc는 슈퍼유저 비밀번호를 받지 않음"; fi
  hashes+=("$h")
done
if [ "$(printf '%s\n' "${hashes[@]}" | sort -u | wc -l | tr -d ' ')" = 3 ]; then ok "AC12 서비스별 DB_PASSWORD가 서로 다름"
else bad "AC12 서비스끼리 같은 DB_PASSWORD를 씀"; fi

# AC14: 모든 컨테이너가 떠 있음 (로그인·직원 등록·발주는 사람이 확인)
running="$(docker compose ps --status running --services)"
for svc in postgres kafka mailpit auth user order gateway; do
  if grep -qx "$svc" <<<"$running"; then ok "AC14 $svc running"; else bad "AC14 $svc가 떠 있지 않음"; fi
done

echo "---- PASS $pass / FAIL $fail / SKIP $skip"
[ "$fail" -eq 0 ]
