package io.mopl.domain.content.mapper;

import io.mopl.domain.content.dto.ContentDto;
import io.mopl.domain.content.dto.ContentStats;
import io.mopl.domain.content.dto.ContentSummary;
import io.mopl.domain.content.dto.request.ContentCreateRequest;
import io.mopl.domain.content.entity.Content;
import java.util.LinkedHashSet;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ContentMapper {

  default Content toEntity(ContentCreateRequest request, String thumbnailUrl) {
    return Content.createManual(
        request.type(),
        request.title(),
        request.description(),
        thumbnailUrl,
        request.tags()
    );
  }

  default ContentDto toDto(Content content, ContentStats stats) {
    ContentStats resolvedStats = stats == null ? ContentStats.empty() : stats;
    return ContentDto.builder()
        .id(content.getId())
        .type(content.getType())
        .title(content.getTitle())
        .description(content.getDescription())
        .thumbnailUrl(content.getThumbnailUrl())
        .tags(new LinkedHashSet<>(content.getTags()))
        .averageRating(resolvedStats.averageRating())
        .reviewCount(resolvedStats.reviewCount())
        .watcherCount(resolvedStats.watcherCount())
        .build();
  }

  default ContentSummary toSummary(Content content, ContentStats stats) {
    ContentStats resolvedStats = stats == null ? ContentStats.empty() : stats;
    return ContentSummary.builder()
        .id(content.getId())
        .type(content.getType())
        .title(content.getTitle())
        .description(content.getDescription())
        .thumbnailUrl(content.getThumbnailUrl())
        .tags(new LinkedHashSet<>(content.getTags()))
        .averageRating(resolvedStats.averageRating())
        .reviewCount(resolvedStats.reviewCount())
        .build();
  }
}
