package io.mopl.domain.content.event;

import java.util.UUID;

public record ContentSoftDeletedEvent(UUID contentId) {
}
