package io.mopl.domain.content.cache.dto;

import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record ContentBaseCache(
    UUID id,
    String title,
    String description,
    ContentType type,
    ContentSource source,
    String thumbnailUrl,
    Set<String> tags
) {

  public ContentBaseCache {
    tags = tags == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(tags));
  }
}
