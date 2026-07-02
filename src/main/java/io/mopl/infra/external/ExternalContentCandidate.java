package io.mopl.infra.external;

import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import java.util.Collection;

public record ExternalContentCandidate(
    ContentType type,
    ContentSource source,
    String externalId,
    String title,
    String description,
    String thumbnailUrl,
    Collection<String> tags
) {
}
