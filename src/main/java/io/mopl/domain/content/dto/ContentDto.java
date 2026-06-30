package io.mopl.domain.content.dto;

import io.mopl.domain.content.entity.ContentType;
import java.util.Set;
import java.util.UUID;
import lombok.Builder;

@Builder
public record ContentDto(
    UUID id,
    ContentType type,
    String title,
    String description,
    String thumbnailUrl,
    Set<String> tags,
    double averageRating,
    int reviewCount,
    long watcherCount
) {
}
