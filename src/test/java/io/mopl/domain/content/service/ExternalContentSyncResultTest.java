package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mopl.domain.content.dto.ExternalContentSyncResult;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ExternalContentSyncResultTest {

  @Test
  void constructor_rejectsInconsistentCounts() {
    assertThatThrownBy(() -> new ExternalContentSyncResult(
        5,
        3,
        1,
        2,
        1,
        0,
        Instant.parse("2026-07-10T00:00:00Z")
    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("수집 결과 건수의 합이 일치하지 않습니다.");
  }
}
