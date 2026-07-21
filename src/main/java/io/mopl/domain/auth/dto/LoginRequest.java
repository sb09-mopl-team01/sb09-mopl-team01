package io.mopl.domain.auth.dto;

public record LoginRequest(
    String username,
    String password
) {
}
