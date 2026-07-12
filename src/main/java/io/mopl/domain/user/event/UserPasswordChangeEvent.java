package io.mopl.domain.user.event;

public record UserPasswordChangeEvent(
    String email
) {
}
