package io.mopl.domain.follow.service;

import io.mopl.domain.follow.dto.FollowCreateRequest;
import io.mopl.domain.follow.dto.FollowDto;
import io.mopl.domain.follow.entity.Follow;
import io.mopl.domain.follow.event.FollowCancelledEvent;
import io.mopl.domain.follow.event.FollowCreatedEvent;
import io.mopl.domain.follow.repository.FollowRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.cache.CacheKey;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FollowService {

  private final FollowRepository followRepository;
  private final UserRepository userRepository;
  private final DomainEventPublisher eventPublisher;
  private final CacheManager cacheManager;

  @Transactional
  public FollowDto follow(UUID followerId, FollowCreateRequest request) {
    if (request == null || request.followeeId() == null) {
      log.warn("Invalid follow request. followerId={}, request={}", followerId, request);
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    User follower = getUser(followerId);
    User followee = getUser(request.followeeId());
    validateFollowTarget(follower, followee);

    if (followRepository.existsByFollowerAndFollowee(follower, followee)) {
      log.warn("Duplicate follow request. followerId={}, followeeId={}",
          follower.getId(), followee.getId());
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
    evictFollowCaches(follower.getId(), followee.getId());
    log.debug("Follow created. followId={}, followerId={}, followeeId={}",
        follow.getId(), follower.getId(), followee.getId());
    return toDto(follow);
  }

  @Transactional
  public void unfollow(UUID followerId, UUID followId) {
    User follower = getUser(followerId);
    Follow follow = followRepository.findById(followId)
        .orElseThrow(() -> {
          log.warn("Unfollow target not found. followerId={}, followId={}", followerId, followId);
          return new BaseException(ErrorCode.FOLLOW_NOT_FOUND);
        });

    if (!follow.isFollowedBy(follower)) {
      log.warn("Unfollow forbidden. requesterId={}, followId={}, ownerId={}",
          follower.getId(), follow.getId(), follow.getFollower().getId());
      throw new BaseException(ErrorCode.FORBIDDEN);
    }

    UUID followeeId = follow.getFollowee().getId();
    followRepository.delete(follow);
    eventPublisher.publish(new FollowCancelledEvent(
        follow.getId(),
        follower.getId(),
        followeeId,
        Instant.now()
    ));
    evictFollowCaches(follower.getId(), followeeId);
    log.debug("Follow deleted. followId={}, followerId={}, followeeId={}",
        follow.getId(), follower.getId(), followeeId);
  }

  @Cacheable(value = CacheKey.FOLLOW, key = "'count:' + #followeeId")
  public long countFollowers(UUID followeeId) {
    User followee = getUser(followeeId);
    long count = followRepository.countByFollowee(followee);
    log.debug("Follower count found. followeeId={}, count={}", followee.getId(), count);
    return count;
  }

  @Cacheable(
      value = CacheKey.FOLLOW,
      key = "'relation:' + #followerId.toString() + ':' + #followeeId.toString()"
  )
  public FollowDto findFollowedByMe(UUID followerId, UUID followeeId) {
    User follower = getUser(followerId);
    User followee = getUser(followeeId);

    return followRepository.findByFollowerAndFollowee(follower, followee)
        .map(this::toDto)
        .orElseThrow(() -> {
          log.warn("Follow relation not found. followerId={}, followeeId={}",
              follower.getId(), followee.getId());
          return new BaseException(ErrorCode.FOLLOW_NOT_FOUND);
        });
  }

  private User getUser(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> {
          log.warn("Follow user not found. userId={}", userId);
          return new BaseException(ErrorCode.USER_NOT_FOUND);
        });
  }

  private void validateFollowTarget(User follower, User followee) {
    if (follower.getId().equals(followee.getId())) {
      log.warn("Self follow request denied. userId={}", follower.getId());
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

  private void evictFollowCaches(UUID followerId, UUID followeeId) {
    evict(CacheKey.FOLLOW, followerCountCacheKey(followeeId));
    evict(CacheKey.FOLLOW, followRelationCacheKey(followerId, followeeId));
  }

  private String followerCountCacheKey(UUID followeeId) {
    return "count:" + followeeId;
  }

  private String followRelationCacheKey(UUID followerId, UUID followeeId) {
    return "relation:" + followerId + ":" + followeeId;
  }

  private void evict(String cacheName, Object key) {
    Cache cache = cacheManager.getCache(cacheName);
    if (cache != null) {
      cache.evict(key);
    }
  }
}
