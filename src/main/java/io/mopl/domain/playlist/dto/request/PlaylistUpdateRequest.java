package io.mopl.domain.playlist.dto.request;

public record PlaylistUpdateRequest(
    String title,
    String description
) {}
