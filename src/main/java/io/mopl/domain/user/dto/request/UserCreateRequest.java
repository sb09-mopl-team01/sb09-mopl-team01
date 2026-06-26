package io.mopl.domain.user.dto.request;

public record UserCreateRequest(
    String email,
    String password,
    String name
) {}