package io.mopl.domain.content.dto;

public record ContentThumbnailCleanupResult(
    int discoveredCount,
    int cleanedCount,
    int skippedCount,
    int failedCount
) {
}
