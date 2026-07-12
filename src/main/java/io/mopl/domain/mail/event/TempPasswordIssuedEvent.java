package io.mopl.domain.mail.event;

public record TempPasswordIssuedEvent(
    String email,
    String tempPassword
) {
}
