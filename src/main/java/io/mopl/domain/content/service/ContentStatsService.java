package io.mopl.domain.content.service;

import io.mopl.domain.content.dto.ContentStats;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.watchingsession.repository.WatchingSessionRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentStatsService {

  private final WatchingSessionRepository watchingSessionRepository;

  public ContentStats getStats(Content content) {
    if (content == null) {
      return ContentStats.empty();
    }
    long watcherCount = getWatcherCount(content.getId());
    return new ContentStats(content.getAverageRating(), content.getReviewCount(), watcherCount);
  }

  public long getWatcherCount(UUID contentId) {
    return watchingSessionRepository.countByContentId(contentId, null);
  }

  public Map<UUID, Long> getWatcherCounts(Collection<UUID> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return Map.of();
    }
    return watchingSessionRepository.countByContentIds(List.copyOf(contentIds));
  }

  public Map<UUID, ContentStats> getStatsByContents(Collection<Content> contents) {
    if (contents == null || contents.isEmpty()) {
      return Map.of();
    }

    List<UUID> contentIds = contents.stream()
        .map(Content::getId)
        .toList();
    Map<UUID, Long> watcherCountsByContentId = getWatcherCounts(contentIds);

    Map<UUID, ContentStats> statsByContentId = new LinkedHashMap<>();
    for (Content content : contents) {
      long watcherCount = watcherCountsByContentId.getOrDefault(content.getId(), 0L);
      statsByContentId.put(
          content.getId(),
          new ContentStats(content.getAverageRating(), content.getReviewCount(), watcherCount)
      );
    }
    return statsByContentId;
  }
}
