package io.mopl.domain.content.dto;

import java.time.Instant;

public record ExternalContentSyncResult(
    int fetchedCount,
    int createdCount,
    int skippedCount,
    int failedCount,
    Instant syncedAt
) {

  public ExternalContentSyncResult {
    if (fetchedCount < 0 || createdCount < 0 || skippedCount < 0 || failedCount < 0) {
      throw new IllegalArgumentException("수집 결과 건수는 음수일 수 없습니다.");
    }
  }
}
