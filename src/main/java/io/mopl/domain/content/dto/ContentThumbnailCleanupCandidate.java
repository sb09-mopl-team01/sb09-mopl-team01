package io.mopl.domain.content.dto;

import java.time.Instant;
import java.util.UUID;

public record ContentThumbnailCleanupCandidate(
    UUID contentId,
    String thumbnailKey,
    Instant deletedAt
) {
}
