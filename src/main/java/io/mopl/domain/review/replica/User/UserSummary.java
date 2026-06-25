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

}
