package io.mopl.domain.user.service;

import io.mopl.domain.user.event.UserLockedEvent;
import io.mopl.domain.user.event.UserPasswordChangeEvent;
import io.mopl.domain.user.event.UserUnlockedEvent;
import io.mopl.domain.user.exception.DuplicateUserEmailException;
import io.mopl.domain.user.exception.UserNotFoundException;
import io.mopl.domain.user.storage.ProfileImageStorage;
import io.mopl.global.cache.CacheKey;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.request.ChangePasswordRequest;
import io.mopl.domain.user.dto.request.UserCreateRequest;
import io.mopl.domain.user.dto.request.UserLockUpdateRequest;
import io.mopl.domain.user.dto.request.UserRoleUpdateRequest;
import io.mopl.domain.user.dto.request.UserUpdateRequest;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.event.UserRoleChangedEvent;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final ProfileImageStorage profileImageStorage;
  private final DomainEventPublisher eventPublisher;

  @Transactional
  public UserDto createUser(UserCreateRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      log.warn("User Create Failed. Email duplication. email={}", request.email());
      throw new DuplicateUserEmailException();
    }

    User user = User.builder()
        .email(request.email())
        .passwordHash(passwordEncoder.encode(request.password()))
        .name(request.name())
        .build();

    User savedUser = userRepository.save(user);

    log.info("User Create Completed. id={}", savedUser.getId());
    return userMapper.toDto(savedUser);
  }

  @Cacheable(value = CacheKey.USER, key = "#userId")
  public UserDto findUser(UUID userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> {
          log.warn("User Single Read Failed. User not found. id={}", userId);
          return new UserNotFoundException();
        });
    log.info("User Single Read Completed. id={}", userId);
    return userMapper.toDto(user);
  }

  @CachePut(value = CacheKey.USER, key = "#userId")
  @Transactional
  public UserDto updateProfileInfo(UUID userId, UserUpdateRequest request, String newImageUrl) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> {
          log.warn("User Update Profile Failed. User not found. id={}", userId);
          return new UserNotFoundException();
        });

    user.updateProfile(request.name(), newImageUrl);

    log.info("User Update Profile Info Completed. id={}", userId);
    return userMapper.toDto(user);
  }

  @CacheEvict(value = CacheKey.USER, key = "#userId")
  @Transactional
  public void updateUserRole(UUID userId, UserRoleUpdateRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> {
          log.warn("User Update Role Failed. User not found. id={}", userId);
          return new UserNotFoundException();
        });
    Role previousRole = user.getRole();
    user.updateRole(request.role());
    if (previousRole != request.role()) {
      eventPublisher.publish(new UserRoleChangedEvent(
          user.getId(),
          user.getRole(),
          Instant.now()
      ));
    }
    log.info("User Update Role Completed. id={}, role={}", userId, user.getRole());
  }

  @CacheEvict(value = CacheKey.USER, key = "#userId")
  @Transactional
  public void changePassword(UUID userId, ChangePasswordRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> {
          log.warn("User Update Password Failed. User not found. id={}", userId);
          return new UserNotFoundException();
        });

    String newPasswordHash = passwordEncoder.encode(request.password());
    user.changePassword(newPasswordHash);

    eventPublisher.publish(new UserPasswordChangeEvent(user.getEmail()));

    log.info("User Update Password Completed. id={}", userId);
  }

  @CacheEvict(value = CacheKey.USER, key = "#userId")
  @Transactional
  public void updateUserLockStatus(UUID userId, UserLockUpdateRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> {
          log.warn("User Update LockStatus Failed. User not found. id={}", userId);
          return new UserNotFoundException();
        });

    if (request.locked()) {
      user.lockAccount();
      eventPublisher.publish(new UserLockedEvent(user.getId(), user.getEmail()));
    } else {
      user.unlockAccount();
      eventPublisher.publish(new UserUnlockedEvent(user.getId()));
    }

    log.info("User Update LockStatus Completed. id={}", userId);
  }

  public CursorResponse<UserDto> findUsers(
      String emailLike, String roleEqual, Boolean isLocked,
      String cursor, UUID idAfter, int limit,
      String sortBy, SortDirection sortDirection) {

    log.info("User Multiple Read Started. emailLike={}, roleEqual={}, isLocked={}, cursor={}, idAfter={}, limit={}, sortBy={}, sortDirection={}",
        emailLike, roleEqual, isLocked, cursor, idAfter, limit, sortBy, sortDirection);

    CursorResponse<User> entityResponse = userRepository.findUsersByCursor(
        emailLike, roleEqual, isLocked, cursor, idAfter, limit, sortBy, sortDirection
    );

    List<UserDto> dtoList = entityResponse.data().stream()
        .map(userMapper::toDto)
        .toList();

    return new CursorResponse<>(
        dtoList,
        entityResponse.nextCursor(),
        entityResponse.nextIdAfter(),
        entityResponse.hasNext(),
        entityResponse.totalCount(),
        entityResponse.sortBy(),
        entityResponse.sortDirection()
    );
  }
}
