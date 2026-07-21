package io.mopl.domain.watchingsession.websocket;

import java.util.UUID;

public record WatchingSessionSubscription(
    UUID watcherId,
    UUID contentId
) {
}
