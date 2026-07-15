package io.mopl.domain.mail.event;

import java.util.UUID;

public record TempPasswordIssuedEvent(
    UUID userId,
    String tempPassword
) {
}
