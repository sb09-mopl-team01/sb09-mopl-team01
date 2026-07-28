package io.mopl.global.sse;

import java.util.UUID;

public record SseConnectedEvent(
    UUID receiverId,
    UUID emitterId
) {
}
