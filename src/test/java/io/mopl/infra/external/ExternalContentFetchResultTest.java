package io.mopl.infra.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExternalContentFetchResultTest {

  @Test
  void merge_sumsCountsAndPreservesCandidateOrder() {
    ExternalContentCandidate first = candidate("first");
    ExternalContentCandidate second = candidate("second");
    ExternalContentFetchResult left = new ExternalContentFetchResult(2, List.of(first), 1, 0);
    ExternalContentFetchResult right = new ExternalContentFetchResult(2, List.of(second), 0, 1);

    ExternalContentFetchResult result = left.merge(right);

    assertThat(result.fetchedCount()).isEqualTo(4);
    assertThat(result.candidates()).containsExactly(first, second);
    assertThat(result.filteredCount()).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(1);
  }

  @Test
  void constructor_rejectsInconsistentCounts() {
    assertThatThrownBy(() -> new ExternalContentFetchResult(1, List.of(), 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("외부 콘텐츠 수집 결과 건수의 합이 일치하지 않습니다.");
  }

  private ExternalContentCandidate candidate(String externalId) {
    return new ExternalContentCandidate(null, null, externalId, null, null, null, List.of());
  }
}
