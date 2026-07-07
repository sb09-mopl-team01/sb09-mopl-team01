package io.mopl.domain.follow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.mopl.domain.follow.dto.FollowCreateRequest;
import io.mopl.domain.follow.dto.FollowDto;
import io.mopl.domain.follow.entity.Follow;
import io.mopl.domain.follow.event.FollowCreatedEvent;
import io.mopl.domain.follow.repository.FollowRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

  @InjectMocks
  private FollowService followService;

  @Mock
  private FollowRepository followRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private DomainEventPublisher eventPublisher;

  private UUID followerId;
  private UUID followeeId;
  private User follower;
  private User followee;

  @BeforeEach
  void setUp() {
    followerId = UUID.randomUUID();
    followeeId = UUID.randomUUID();
    follower = createUser(followerId, "follower");
    followee = createUser(followeeId, "followee");
  }

  @Test
  @DisplayName("팔로우 생성 성공")
  void follow() {
    Follow savedFollow = Follow.create(follower, followee);
    UUID followId = UUID.randomUUID();
    ReflectionTestUtils.setField(savedFollow, "id", followId);
    given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
    given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
    given(followRepository.existsByFollowerAndFollowee(follower, followee)).willReturn(false);
    given(followRepository.save(any(Follow.class))).willReturn(savedFollow);

    FollowDto result = followService.follow(followerId, new FollowCreateRequest(followeeId));

    assertThat(result.id()).isEqualTo(followId);
    assertThat(result.followerId()).isEqualTo(followerId);
    assertThat(result.followeeId()).isEqualTo(followeeId);
    verify(followRepository).save(any(Follow.class));
    verify(eventPublisher).publish(argThat(event -> {
      if (!(event instanceof FollowCreatedEvent followCreatedEvent)) {
        return false;
      }
      return followCreatedEvent.followId().equals(followId)
          && followCreatedEvent.followerId().equals(followerId)
          && followCreatedEvent.followerName().equals("follower")
          && followCreatedEvent.followeeId().equals(followeeId)
          && followCreatedEvent.occurredAt() != null;
    }));
  }

  @Test
  @DisplayName("자기 자신 팔로우는 INVALID_INPUT 예외로 차단")
  void rejectSelfFollow() {
    given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
    given(userRepository.findById(followerId)).willReturn(Optional.of(follower));

    assertThatThrownBy(() -> followService.follow(followerId, new FollowCreateRequest(followerId)))
        .isInstanceOf(BaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT);
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  @DisplayName("중복 팔로우는 ALREADY_FOLLOWING 예외로 차단")
  void rejectDuplicateFollow() {
    given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
    given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
    given(followRepository.existsByFollowerAndFollowee(follower, followee)).willReturn(true);

    assertThatThrownBy(() -> followService.follow(followerId, new FollowCreateRequest(followeeId)))
        .isInstanceOf(BaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_FOLLOWING);
    verify(eventPublisher, never()).publish(any());
  }

  @Test
  @DisplayName("팔로우 취소 성공")
  void unfollow() {
    Follow follow = Follow.create(follower, followee);
    UUID followId = UUID.randomUUID();
    ReflectionTestUtils.setField(follow, "id", followId);
    given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
    given(followRepository.findById(followId)).willReturn(Optional.of(follow));

    followService.unfollow(followerId, followId);

    verify(followRepository).delete(follow);
  }

  @Test
  @DisplayName("요청자 본인의 팔로우가 아니면 FORBIDDEN 예외로 차단")
  void rejectUnfollowByOtherUser() {
    UUID otherUserId = UUID.randomUUID();
    User otherUser = createUser(otherUserId, "other");
    Follow follow = Follow.create(follower, followee);
    UUID followId = UUID.randomUUID();
    ReflectionTestUtils.setField(follow, "id", followId);
    given(userRepository.findById(otherUserId)).willReturn(Optional.of(otherUser));
    given(followRepository.findById(followId)).willReturn(Optional.of(follow));

    assertThatThrownBy(() -> followService.unfollow(otherUserId, followId))
        .isInstanceOf(BaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
  }

  @Test
  @DisplayName("특정 유저의 팔로워 수 조회 성공")
  void countFollowers() {
    given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
    given(followRepository.countByFollowee(followee)).willReturn(3L);

    long result = followService.countFollowers(followeeId);

    assertThat(result).isEqualTo(3L);
  }

  @Test
  @DisplayName("내가 특정 유저를 팔로우 중이면 FollowDto를 반환")
  void findFollowedByMe() {
    Follow follow = Follow.create(follower, followee);
    UUID followId = UUID.randomUUID();
    ReflectionTestUtils.setField(follow, "id", followId);
    given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
    given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
    given(followRepository.findByFollowerAndFollowee(follower, followee))
        .willReturn(Optional.of(follow));

    FollowDto result = followService.findFollowedByMe(followerId, followeeId);

    assertThat(result.id()).isEqualTo(followId);
    assertThat(result.followerId()).isEqualTo(followerId);
    assertThat(result.followeeId()).isEqualTo(followeeId);
  }

  @Test
  @DisplayName("특정 유저를 팔로우하지 않으면 FOLLOW_NOT_FOUND 예외 발생")
  void rejectFindFollowedByMeWhenNotFollowing() {
    given(userRepository.findById(followerId)).willReturn(Optional.of(follower));
    given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
    given(followRepository.findByFollowerAndFollowee(follower, followee))
        .willReturn(Optional.empty());

    assertThatThrownBy(() -> followService.findFollowedByMe(followerId, followeeId))
        .isInstanceOf(BaseException.class)
        .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FOLLOW_NOT_FOUND);
  }

  private User createUser(UUID id, String name) {
    User user = User.builder()
        .email(name + "@example.com")
        .passwordHash("password")
        .name(name)
        .build();
    ReflectionTestUtils.setField(user, "id", id);
    return user;
  }
}
