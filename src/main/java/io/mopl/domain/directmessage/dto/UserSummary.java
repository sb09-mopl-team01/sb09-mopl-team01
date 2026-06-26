package io.mopl.domain.directmessage.dto;

import java.util.UUID;

public record UserSummary(
    UUID userId,
    String name,
    String profileImageUrl
) {
// 유저 정보 요약 메소드(프로필이미지 필요, 추후 수정 필요)
  public static UserSummary from(UUID userId) {
    return new UserSummary(userId, userId.toString(), null);
  }
}
