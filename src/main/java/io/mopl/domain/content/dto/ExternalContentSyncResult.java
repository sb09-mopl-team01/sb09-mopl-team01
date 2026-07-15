package io.mopl.domain.content.dto;

import java.time.Instant;

/**
 * 외부 콘텐츠 수집부터 DB 동기화까지의 실행 결과입니다.
 *
 * @param fetchedCount 외부 API 응답에서 받은 원본 항목 수
 * @param acceptedCount 매핑과 수집 정책을 통과해 동기화를 시도한 후보 수
 * @param filteredCount 언어 등 명시적인 수집 정책으로 제외한 항목 수
 * @param createdCount 새로 저장한 콘텐츠 수
 * @param skippedCount 기존 콘텐츠로 확인되어 동기화 시각만 갱신한 콘텐츠 수
 * @param failedCount 개별 매핑 또는 후보 검증에 실패한 항목 수
 * @param syncedAt 이번 실행에서 사용한 동기화 기준 시각
 */
public record ExternalContentSyncResult(
    int fetchedCount,
    int acceptedCount,
    int filteredCount,
    int createdCount,
    int skippedCount,
    int failedCount,
    Instant syncedAt
) {

  public ExternalContentSyncResult {
    if (fetchedCount < 0 || acceptedCount < 0 || filteredCount < 0
        || createdCount < 0 || skippedCount < 0 || failedCount < 0) {
      throw new IllegalArgumentException("수집 결과 건수는 음수일 수 없습니다.");
    }
    int mappingFailedCount = fetchedCount - acceptedCount - filteredCount;
    int validationFailedCount = acceptedCount - createdCount - skippedCount;
    if (mappingFailedCount < 0 || validationFailedCount < 0
        || failedCount != mappingFailedCount + validationFailedCount) {
      throw new IllegalArgumentException("수집 결과 건수의 합이 일치하지 않습니다.");
    }
  }
}
