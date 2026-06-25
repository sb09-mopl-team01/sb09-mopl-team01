package io.mopl.domain.review.replica.User;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class UserSummary {
  private UUID userId;
  private String name;
  private String profileImageUrl;

  public static UserSummary from(User user) {
    return UserSummary.builder()
        .userId(user.getId())
        .name("임시유저")
        .profileImageUrl("임시URL")
        .build();
  }
}
