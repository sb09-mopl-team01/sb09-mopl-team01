package io.mopl.domain.directmessage.dto;

import java.util.UUID;

public record UserSummary(
    UUID userId,
    String name,
    String profileImageUrl
) {

  public static UserSummary from(UUID userId) {
    return new UserSummary(userId, userId.toString(), null);
  }
}
