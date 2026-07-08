package io.mopl.domain.follow.service;

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
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FollowService {

  private final FollowRepository followRepository;
  private final UserRepository userRepository;
  private final DomainEventPublisher eventPublisher;

  @Transactional
  public FollowDto follow(UUID followerId, FollowCreateRequest request) {
    User follower = getUser(followerId);
    User followee = getUser(request.followeeId());
    validateFollowTarget(follower, followee);

    if (followRepository.existsByFollowerAndFollowee(follower, followee)) {
      throw new BaseException(ErrorCode.ALREADY_FOLLOWING);
    }

    Follow follow = followRepository.save(Follow.create(follower, followee));
    eventPublisher.publish(new FollowCreatedEvent(
        follow.getId(),
        follower.getId(),
        follower.getName(),
        followee.getId(),
        Instant.now()
    ));
    return toDto(follow);
  }

  @Transactional
  public void unfollow(UUID followerId, UUID followId) {
    User follower = getUser(followerId);
    Follow follow = followRepository.findById(followId)
        .orElseThrow(() -> new BaseException(ErrorCode.FOLLOW_NOT_FOUND));

    if (!follow.isFollowedBy(follower)) {
      throw new BaseException(ErrorCode.FORBIDDEN);
    }

    followRepository.delete(follow);
  }

  public long countFollowers(UUID followeeId) {
    User followee = getUser(followeeId);
    return followRepository.countByFollowee(followee);
  }

  public FollowDto findFollowedByMe(UUID followerId, UUID followeeId) {
    User follower = getUser(followerId);
    User followee = getUser(followeeId);

    return followRepository.findByFollowerAndFollowee(follower, followee)
        .map(this::toDto)
        .orElseThrow(() -> new BaseException(ErrorCode.FOLLOW_NOT_FOUND));
  }

  private User getUser(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new BaseException(ErrorCode.USER_NOT_FOUND));
  }

  private void validateFollowTarget(User follower, User followee) {
    if (follower.getId().equals(followee.getId())) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }

  private FollowDto toDto(Follow follow) {
    return FollowDto.builder()
        .id(follow.getId())
        .followeeId(follow.getFollowee().getId())
        .followerId(follow.getFollower().getId())
        .build();
  }
}
