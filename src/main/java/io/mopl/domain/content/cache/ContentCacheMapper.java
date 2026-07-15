package io.mopl.domain.content.cache;

import io.mopl.domain.content.cache.dto.ContentBaseCache;
import io.mopl.domain.content.cache.dto.ContentStatsCache;
import io.mopl.domain.content.dto.ContentDto;
import io.mopl.domain.content.entity.Content;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Component;

@Component
public class ContentCacheMapper {

  public ContentBaseCache toBase(Content content) {
    return new ContentBaseCache(
        content.getId(),
        content.getTitle(),
        content.getDescription(),
        content.getType(),
        content.getSource(),
        content.getThumbnailUrl(),
        content.getTags()
    );
  }

  public ContentStatsCache toStats(Content content) {
    return new ContentStatsCache(
        content.getId(),
        content.getAverageRating(),
        content.getReviewCount()
    );
  }

  public ContentDto toDto(ContentCacheSnapshot snapshot, long watcherCount) {
    ContentBaseCache base = snapshot.base();
    ContentStatsCache stats = snapshot.stats();
    return ContentDto.builder()
        .id(base.id())
        .type(base.type())
        .title(base.title())
        .description(base.description())
        .thumbnailUrl(base.thumbnailUrl())
        .tags(new LinkedHashSet<>(base.tags()))
        .averageRating(stats.averageRating())
        .reviewCount(stats.reviewCount())
        .watcherCount(watcherCount)
        .build();
  }
}
