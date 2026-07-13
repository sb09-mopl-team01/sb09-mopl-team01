package io.mopl.infra.external;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 외부 API 응답을 동기화 후보로 변환한 결과입니다.
 *
 * @param fetchedCount 외부 API 응답에 포함된 원본 항목 수
 * @param candidates 매핑과 수집 정책을 통과한 동기화 후보
 * @param filteredCount 언어 등 명시적인 수집 정책에 따라 제외한 항목 수
 * @param failedCount 개별 항목 매핑에 실패한 항목 수
 */
public record ExternalContentFetchResult(
    int fetchedCount,
    List<ExternalContentCandidate> candidates,
    int filteredCount,
    int failedCount
) {

  public ExternalContentFetchResult {
    candidates = candidates == null ? List.of() : List.copyOf(candidates);
    if (fetchedCount < 0 || filteredCount < 0 || failedCount < 0) {
      throw new IllegalArgumentException("외부 콘텐츠 수집 결과 건수는 음수일 수 없습니다.");
    }
    if (fetchedCount != candidates.size() + filteredCount + failedCount) {
      throw new IllegalArgumentException("외부 콘텐츠 수집 결과 건수의 합이 일치하지 않습니다.");
    }
  }

  public static ExternalContentFetchResult empty() {
    return new ExternalContentFetchResult(0, List.of(), 0, 0);
  }

  public static ExternalContentFetchResult accepted(List<ExternalContentCandidate> candidates) {
    List<ExternalContentCandidate> resolvedCandidates = candidates == null ? List.of() : candidates;
    return new ExternalContentFetchResult(
        resolvedCandidates.size(),
        resolvedCandidates,
        0,
        0
    );
  }

  public ExternalContentFetchResult merge(ExternalContentFetchResult other) {
    Objects.requireNonNull(other, "병합할 외부 콘텐츠 수집 결과는 필수입니다.");
    List<ExternalContentCandidate> mergedCandidates = new ArrayList<>(candidates);
    mergedCandidates.addAll(other.candidates);
    return new ExternalContentFetchResult(
        fetchedCount + other.fetchedCount,
        mergedCandidates,
        filteredCount + other.filteredCount,
        failedCount + other.failedCount
    );
  }

  public int acceptedCount() {
    return candidates.size();
  }
}
