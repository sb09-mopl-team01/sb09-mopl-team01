package io.mopl.domain.content.service;

import io.mopl.domain.content.dto.ContentStats;
import io.mopl.domain.content.entity.Content;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ContentStatsService {

  public ContentStats getStats(Content content) {
    if (content == null) {
      return ContentStats.empty();
    }
    return new ContentStats(content.getAverageRating(), content.getReviewCount(), 0L);
  }

  public Map<UUID, ContentStats> getStatsByContents(Collection<Content> contents) {
    if (contents == null || contents.isEmpty()) {
      return Map.of();
    }

    Map<UUID, ContentStats> statsByContentId = new LinkedHashMap<>();
    for (Content content : contents) {
      statsByContentId.put(content.getId(), getStats(content));
    }
    return statsByContentId;
  }
}
