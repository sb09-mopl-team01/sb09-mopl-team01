package io.mopl.domain.playlist.dto;

import io.mopl.domain.content.dto.ContentSummary;
import io.mopl.domain.user.dto.response.UserSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PlaylistDto(
    UUID id,
    UserSummary owner,
    String title,
    String description,
    Instant updatedAt,
    Long subscriberCount,
    boolean subscribedByMe,
    List<ContentSummary> contents
) {}