package io.mopl.domain.follow.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.mopl.domain.follow.dto.FollowCreateRequest;
import io.mopl.domain.follow.dto.FollowDto;
import io.mopl.domain.follow.service.FollowService;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class FollowControllerTest {

  @InjectMocks
  private FollowController followController;

  @Mock
  private FollowService followService;

  @Test
  @DisplayName("POST /api/follows - 팔로우 성공")
  void follow() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    FollowCreateRequest request = new FollowCreateRequest(followeeId);
    FollowDto expected = FollowDto.builder()
        .id(UUID.randomUUID())
        .followerId(followerId)
        .followeeId(followeeId)
        .build();
    given(followService.follow(followerId, request)).willReturn(expected);

    ResponseEntity<FollowDto> response = followController.follow(followerId, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isEqualTo(expected);
  }

  @Test
  @DisplayName("DELETE /api/follows/{followId} - 팔로우 취소 성공")
  void unfollow() {
    UUID followerId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    ResponseEntity<Void> response = followController.unfollow(followerId, followId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(followService).unfollow(followerId, followId);
  }

  @Test
  @DisplayName("GET /api/follows/count - 특정 유저 팔로워 수 조회 성공")
  void countFollowers() {
    UUID followeeId = UUID.randomUUID();
    given(followService.countFollowers(followeeId)).willReturn(3L);

    ResponseEntity<Long> response = followController.countFollowers(followeeId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(3L);
  }

  @Test
  @DisplayName("GET /api/follows/followed-by-me - 특정 유저 팔로잉 여부 조회 성공")
  void findFollowedByMe() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    FollowDto expected = FollowDto.builder()
        .id(UUID.randomUUID())
        .followerId(followerId)
        .followeeId(followeeId)
        .build();
    given(followService.findFollowedByMe(followerId, followeeId)).willReturn(expected);

    ResponseEntity<FollowDto> response = followController.findFollowedByMe(followerId, followeeId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expected);
  }

  @Test
  @DisplayName("GET /api/follows/followed-by-me - 팔로우하지 않으면 FOLLOW_NOT_FOUND 예외 발생")
  void findFollowedByMeWhenNotFollowing() {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    given(followService.findFollowedByMe(followerId, followeeId))
        .willThrow(new BaseException(ErrorCode.FOLLOW_NOT_FOUND));

    assertThatThrownBy(() -> followController.findFollowedByMe(followerId, followeeId))
        .isInstanceOf(BaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FOLLOW_NOT_FOUND);
  }
}
