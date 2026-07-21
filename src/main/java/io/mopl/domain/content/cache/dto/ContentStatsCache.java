package io.mopl.domain.content.cache.dto;

import java.util.UUID;

public record ContentStatsCache(
    UUID contentId,
    double averageRating,
    int reviewCount
) {
}
