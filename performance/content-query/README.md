# 콘텐츠 조회 성능 실험실

Content 조회 API를 실제 PostgreSQL과 Redis에서 반복 측정하기 위한 격리 환경이다.

## 원칙

- 공용 RDS와 공용 Redis를 사용하지 않는다.
- 성능 DB는 Docker의 `mopl_perf` 데이터베이스만 사용한다.
- 개선 전후에 동일한 데이터와 요청 조건을 사용한다.
- 원본 측정 결과는 `build/performance-results/content-query`에 저장하고 Git에 커밋하지 않는다.
- 인덱스나 운영 코드는 기준 성능과 실행계획을 보고한 뒤 수정한다.

> 주의: `seed.sql`은 데이터를 초기화하고 벤치마크는 Redis `FLUSHDB`를 실행한다. 반드시 아래 성능 전용 PostgreSQL·Redis에만 사용한다. `seed.sql`은 DB 이름이 `mopl_perf`가 아니면 즉시 실패한다.

## 포트

- PostgreSQL: `localhost:15432`
- Redis: `localhost:16379`
- 성능 테스트 애플리케이션: `localhost:18080`

비밀번호는 외부 시스템과 공유하지 않는 로컬 성능 컨테이너 전용 값이다.

## 실행 순서

```powershell
docker compose -f performance/content-query/docker-compose.yml up -d
docker cp performance/content-query/seed.sql mopl-perf-postgres:/tmp/seed.sql
docker exec mopl-perf-postgres psql -U mopl_perf -d mopl_perf -f /tmp/seed.sql
```

API 벤치마크는 실행 중인 로컬 애플리케이션과 `ADMIN_EMAIL`, `ADMIN_PASSWORD` 환경 변수가 필요하다.

```powershell
python performance/content-query/benchmark_api.py `
  --confirm-flush-performance-redis `
  --output build/performance-results/content-query/api-baseline.json
```

최종 적용 결과와 결정은 `final-report.md`를 기준으로 한다. 상세 원본 JSON과 실행 로그는 `build/performance-results/content-query`에 생성되며 커밋하지 않는다.
