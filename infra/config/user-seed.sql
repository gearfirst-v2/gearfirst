-- 로컬용 기초 데이터. user 서비스가 시작할 때마다 실행되므로 이미 있으면 넣지 않는다.
-- order 서비스는 work_type_name이 정확히 '본사', '창고', '대리점'인지로 권한을 판단한다.
INSERT INTO work_type (work_type_name, created_at, updated_at)
SELECT v, now(), now() FROM (VALUES ('본사'), ('창고'), ('대리점')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM work_type w WHERE w.work_type_name = t.v);

INSERT INTO region (region_name, created_at, updated_at)
SELECT v, now(), now() FROM (VALUES ('서울'), ('부산'), ('대구'), ('인천'), ('울산')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM region r WHERE r.region_name = t.v);
