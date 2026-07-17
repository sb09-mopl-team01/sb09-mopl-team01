# 콘텐츠 조회 100,000건 성능 최적화 최종 보고서

## 1. 결론

콘텐츠 100,000건 환경에서 조회 API의 실제 병목을 측정하고, 효과가 검증된 변경만 운영 코드에 반영했다.

- 3글자 이상 제목·설명 검색에 PostgreSQL `pg_trgm` GIN 인덱스를 적용했다.
- 1~2글자 및 인덱싱할 수 없는 검색어는 `LOCATE` 계열 안전 경로로 분리했다.
- 중복 행이 발생하지 않는 현재 쿼리 구조에 맞춰 불필요한 `DISTINCT`와 `COUNT(DISTINCT ...)`를 제거했다.
- 생성일·평점 깊은 커서는 복합 인덱스 순서와 같은 행 값 비교를 사용하도록 변경했다.
- 시청자순 네이티브 쿼리, 검색 조건 캐시, 별도 검색 엔진은 측정 후 이번 범위에서 제외했다.
- 기존 마이그레이션 V1~V5는 수정하지 않고 V6으로 검색 인덱스를 추가했다.

## 2. 검증 환경

| 항목 | 값 |
|---|---:|
| PostgreSQL | 16.14 |
| Redis | 7.4.9 |
| Content | 100,000건 |
| Content Tag | 300,000건 |
| Review | 300,000건 |
| Watching Session | 20,000건 |
| 동시 요청 | 100회, 작업자 10명 |

모든 성능 데이터는 공용·배포 DB가 아닌 전용 Docker DB에서 생성했다. 검색, 필터, 정렬, 깊은 커서와 Redis 캐시가 실제 API 응답에서도 동작하는지 함께 검증했다.

## 3. 적용한 변경

### 3.1 키워드 검색

- 연속된 유니코드 문자·숫자가 3개 이상이면 `containsIgnoreCase`가 만드는 `LIKE '%keyword%'`와 GIN 인덱스를 사용한다.
- 1~2글자 또는 인덱싱할 수 없는 검색어는 QueryDSL `indexOf`로 `LOCATE`/`strpos` 경로를 사용한다.
- `%`, `_`, `!` 같은 문자를 와일드카드가 아닌 검색어 그대로 처리한다.

### 3.2 목록과 totalCount

- 일반 목록은 Content가 루트이고 태그가 `EXISTS` 조건이므로 행 중복이 발생하지 않는다.
- 이에 따라 목록의 `DISTINCT`를 제거하고 `COUNT(DISTINCT content.id)`를 `COUNT(content.id)`로 변경했다.
- 다중 태그가 동시에 일치해도 한 Content가 한 번만 집계되는 회귀 테스트를 추가했다.

### 3.3 깊은 커서

- 기존 OR 조건을 `(sortValue, id) < 또는 > (cursor, idAfter)` 행 값 비교로 변경했다.
- PostgreSQL이 `(created_at, id)`, `(average_rating, id)` 복합 인덱스의 정확한 시작 위치로 이동할 수 있게 했다.
- 오름차순·내림차순과 동일 평점 데이터에서 중복·누락이 없는지 검증했다.

### 3.4 V6 마이그레이션

파일: `src/main/resources/db/migration/V6__add_content_search_trigram_indexes.sql`

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_contents_title_trgm
    ON contents USING gin (lower(title) gin_trgm_ops);

CREATE INDEX idx_contents_description_trgm
    ON contents USING gin (lower(description) gin_trgm_ops);
```

- 팀 결정에 따라 일반 `CREATE INDEX`를 사용했다.
- 100,000건 DB에서 두 인덱스 생성 시간은 합계 약 1.80초였다.
- 인덱스 크기는 제목 3,312kB, 설명 4,864kB였다.
- 빈 DB에서 애플리케이션을 기동해 Flyway V1→V6이 모두 성공하고 스키마가 v6이 되는 것을 확인한다.

## 4. API 전후 비교

동일한 100회 동시 요청의 p95를 비교했다. 이전 값은 `api-comparison-no-trigram.json`, 최종 값은 `api-final-all-scenarios.json` 기준이다.

| 시나리오 | 개선 전 p95 | 개선 후 p95 | 변화 |
|---|---:|---:|---:|
| 최신순 | 78.169ms | 28.383ms | 63.7% 감소 |
| 영화 필터 | 70.706ms | 38.174ms | 46.0% 감소 |
| 2글자 검색 | 799.023ms | 247.665ms | 69.0% 감소 |
| 3글자 검색 | 468.841ms | 27.711ms | 94.1% 감소 |
| 태그 검색 | 103.268ms | 86.140ms | 16.6% 감소 |
| 평점순 | 55.256ms | 23.147ms | 58.1% 감소 |
| 시청자순 | 142.586ms | 94.519ms | 33.7% 감소 |
| 2글자+태그 조합 | 618.159ms | 97.468ms | 84.2% 감소 |

깊은 생성일 커서는 DB 실행 시간이 21.467ms에서 0.074ms로, 깊은 평점 커서는 17.045ms에서 0.078ms로 감소했다. 최종 API 동시 p95는 각각 34.191ms와 34.734ms였다.

## 5. 검토 후 제외한 대안

### 5.1 시청자순 네이티브 쿼리

WatchingSession을 먼저 집계하는 파생 테이블 방식은 DB 실행 시간이 73.562ms에서 31.335ms로 줄었다. 그러나 QueryDSL JPA로 표현할 수 없어 필터·정렬·커서를 모두 네이티브 SQL로 다시 구현해야 한다. 최종 API p95가 94.519ms로 측정됐고 의미 훼손 없이 목표 범위에 들어왔으므로 이번 작업에서는 채택하지 않는다.

### 5.2 검색 조건 및 totalCount 캐시

검색 결과 캐시는 콘텐츠·리뷰·시청 세션 변화에 대한 광범위한 무효화가 필요하다. 현재 확정된 캐시 정책도 검색 조건 캐시를 제외하고 있으므로 적용하지 않는다. Content base/stats 캐시는 측정 중 약 98.5% 적중률로 정상 동작했다.

### 5.3 1~2글자 전용 검색 기술

`pg_bigm`, Elasticsearch/OpenSearch n-gram은 1~2글자 검색을 더 빠르게 할 수 있지만 배포·운영 복잡도가 크게 증가한다. 현재는 정확한 부분 일치 의미를 유지하는 `LOCATE` 경로를 최종 선택한다.

### 5.4 totalCount 제거

API 응답 계약을 바꾸지 않기 위해 totalCount는 유지했다. 대신 현재 쿼리 구조에 안전한 `COUNT(*)` 계열로 비용을 줄였다.

## 6. 테스트 결과

- 콘텐츠 도메인 테스트 전체: 성공
- 추가 회귀 테스트: 1·2·3글자 검색, 특수문자 리터럴 검색, 다중 태그 count, 생성일·평점 커서 오름차순/내림차순 및 중복·누락 검증
- 전체 프로젝트 테스트: 393개 중 타 도메인 커서 테스트 3개가 전체 실행에서 실패
- 실패한 알림·시청 세션 테스트 3개 단독 재실행: 모두 성공

전체 실행 실패는 변경 파일과 무관한 타 도메인의 시간·정렬 비결정성으로 분류한다. 콘텐츠 테스트와 검색 인덱스 마이그레이션 검증은 성공했으며, 버전 변경 후 V1→V6 적용을 다시 확인한다.

## 7. 화면 검증

- 100,000건 최종 목록: `performance/content-query/evidence/ui-optimized-100k.png`
- 3글자 검색: `performance/content-query/evidence/ui-optimized-keyword-romance-100k.png`
- 2글자 검색: `performance/content-query/evidence/ui-optimized-keyword-space-100k.png`

## 8. 재현 자료

- 실행 안내: `performance/content-query/README.md`
- 격리 PostgreSQL·Redis: `performance/content-query/docker-compose.yml`
- 100,000건 데이터 생성: `performance/content-query/seed.sql`
- API 반복 측정: `performance/content-query/benchmark_api.py`
- 화면 증거: `performance/content-query/evidence/`

상세 측정 JSON과 애플리케이션 실행 로그는 `build/performance-results/content-query`에 생성되는 로컬 산출물이므로 커밋하지 않는다. 최종 수치와 채택·제외 근거는 이 보고서에 통합했다.

## 9. 최종 상태

이번 대용량 데이터 조회 성능 최적화 범위에서 계획한 측정, 병목 분석, 코드 개선, 100,000건 재측정, V6 마이그레이션, Flyway 검증, 회귀 테스트와 화면 증거 수집을 완료했다. 별도 검색 엔진이나 시청자 수 비정규화는 미완료 항목이 아니라 이번 검증 결과에 따라 제외한 대안이다.
