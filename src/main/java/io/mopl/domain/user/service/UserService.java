package io.mopl.domain.user.service;

import io.mopl.domain.user.document.UserDocument;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.request.*;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.event.UserLockedEvent;
import io.mopl.domain.user.event.UserPasswordChangeEvent;
import io.mopl.domain.user.event.UserRoleChangedEvent;
import io.mopl.domain.user.event.UserSyncedEvent;
import io.mopl.domain.user.event.UserUnlockedEvent;
import io.mopl.domain.user.exception.DuplicateUserEmailException;
import io.mopl.domain.user.exception.UserNotFoundException;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.domain.user.repository.SocialAccountRepository;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.domain.user.repository.search.UserSearchRepository;
import io.mopl.global.cache.CacheKey;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.OpenSearchCursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final DomainEventPublisher eventPublisher;
  private final SocialAccountRepository socialAccountRepository;

  @Autowired(required = false)
  private UserSearchRepository userSearchRepository;

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

    eventPublisher.publish(new UserSyncedEvent(
        savedUser.getId(),
        savedUser.getName(),
        savedUser.getEmail(),
        savedUser.getRole().name(),
        savedUser.isLocked(),
        savedUser.getCreatedAt()
    ));

    log.info("User Create Completed. id={}", savedUser.getId());
    return userMapper.toDto(savedUser);
  }

  @Cacheable(value = CacheKey.USER, key = "#userId")
  public UserDto findUser(UUID userId) {
    User user = getUserById(userId);
    log.debug("User Single Read Completed. id={}", userId);
    return userMapper.toDto(user);
  }

  @CachePut(value = CacheKey.USER, key = "#userId")
  @Transactional
  public UserDto updateProfileInfo(UUID userId, UserUpdateRequest request, String newImageUrl) {
    User user = getUserById(userId);
    user.updateProfile(request.name(), newImageUrl);

    eventPublisher.publish(new UserSyncedEvent(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.getRole().name(),
        user.isLocked(),
        user.getCreatedAt()
    ));

    log.info("User Update Profile Info Completed. id={}", userId);
    return userMapper.toDto(user);
  }

  @CacheEvict(value = CacheKey.USER, key = "#userId")
  @Transactional
  public void updateUserRole(UUID userId, UserRoleUpdateRequest request) {
    User user = getUserById(userId);
    Role previousRole = user.getRole();

    user.updateRole(request.role());

    if (previousRole != request.role()) {
      eventPublisher.publish(new UserRoleChangedEvent(user.getId(), user.getRole(), Instant.now()));
      eventPublisher.publish(new UserSyncedEvent(
          user.getId(),
          user.getName(),
          user.getEmail(),
          user.getRole().name(),
          user.isLocked(),
          user.getCreatedAt()
      ));
    }

    log.info("User Update Role Completed. id={}, role={}", userId, user.getRole());
  }

  @CacheEvict(value = CacheKey.USER, key = "#userId")
  @Transactional
  public void changePassword(UUID userId, ChangePasswordRequest request) {
    User user = getUserById(userId);
    if (socialAccountRepository.existsByUser(user)) {
      throw new BaseException(ErrorCode.SOCIAL_USER_CANNOT_RESET_PASSWORD);
    }
    String newPasswordHash = passwordEncoder.encode(request.password());

    user.changePassword(newPasswordHash);
    eventPublisher.publish(new UserPasswordChangeEvent(user.getId()));

    log.info("User Update Password Completed. id={}", userId);
  }

  @CacheEvict(value = CacheKey.USER, key = "#userId")
  @Transactional
  public void updateUserLockStatus(UUID userId, UserLockUpdateRequest request) {
    User user = getUserById(userId);

    if (request.locked()) {
      user.lockAccount();
      eventPublisher.publish(new UserLockedEvent(user.getId()));
    } else {
      user.unlockAccount();
      eventPublisher.publish(new UserUnlockedEvent(user.getId()));
    }
    eventPublisher.publish(new UserSyncedEvent(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.getRole().name(),
        user.isLocked(),
        user.getCreatedAt()
    ));

    log.info("User Update LockStatus Completed. id={}, locked={}", userId, request.locked());
  }

  public CursorResponse<UserDto> findUsers(
      String emailLike, String roleEqual, Boolean isLocked,
      String cursor, UUID idAfter, int limit,
      String sortBy, SortDirection sortDirection) {

    log.info("User Multiple Read Started. emailLike={}, roleEqual={}, isLocked={}, cursor={}, idAfter={}, limit={}, sortBy={}, sortDirection={}",
        emailLike, roleEqual, isLocked, cursor, idAfter, limit, sortBy, sortDirection);

    if (shouldUseOpenSearch()) {
      return searchUsersViaOpenSearch(emailLike, roleEqual, isLocked, cursor, limit, sortBy, sortDirection);
    }

    return searchUsersViaDatabase(emailLike, roleEqual, isLocked, cursor, idAfter, limit, sortBy, sortDirection);
  }

  private User getUserById(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> {
          log.warn("User fetch failed. User not found. id={}", userId);
          return new UserNotFoundException();
        });
  }

  private boolean shouldUseOpenSearch() {
    return userSearchRepository != null;
  }

  private CursorResponse<UserDto> searchUsersViaOpenSearch(
      String emailLike, String roleEqual, Boolean isLocked,
      String cursor, int limit, String sortBy, SortDirection sortDirection) {

    if (userSearchRepository == null) {
      log.info("OpenSearch is not available. Fallback to database search.");
      return searchUsersViaDatabase(emailLike, roleEqual, isLocked, cursor, null, limit, sortBy, sortDirection);
    }

    OpenSearchCursorResponse<UserDocument> searchResponse = userSearchRepository.searchUsersByCursor(
        emailLike, roleEqual, isLocked, parseSortValues(cursor), limit, sortBy, sortDirection
    );

    if (searchResponse.content() == null || searchResponse.content().isEmpty()) {
      return new CursorResponse<>(
          List.of(), null, null, false, 0L, sortBy, sortDirection
      );
    }

    List<UUID> userIds = searchResponse.content().stream()
        .map(UserDocument::getId)
        .toList();

    List<User> users = userRepository.findAllByIdIn(userIds);

    Map<UUID, User> userMap = users.stream()
        .collect(Collectors.toMap(User::getId, user -> user));

    List<User> orderedUsers = userIds.stream()
        .map(userMap::get)
        .filter(Objects::nonNull)
        .toList();

    List<UserDto> dtoList = orderedUsers.stream()
        .map(userMapper::toDto)
        .toList();

    String nextCursor = null;
    if (searchResponse.nextSortValues() != null && !searchResponse.nextSortValues().isEmpty()) {
      String joined = searchResponse.nextSortValues().stream()
          .map(Object::toString)
          .collect(Collectors.joining(","));
      nextCursor = java.util.Base64.getEncoder().encodeToString(joined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    UUID nextIdAfter = !userIds.isEmpty() ? userIds.get(userIds.size() - 1) : null;

    return new CursorResponse<>(
        dtoList,
        nextCursor,
        nextIdAfter,
        searchResponse.hasNext(),
        searchResponse.totalCount(),
        sortBy,
        sortDirection
    );
  }

  private CursorResponse<UserDto> searchUsersViaDatabase(
      String emailLike, String roleEqual, Boolean isLocked,
      String cursor, UUID idAfter, int limit,
      String sortBy, SortDirection sortDirection) {

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

  private List<Object> parseSortValues(String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      String decoded = new String(java.util.Base64.getDecoder().decode(cursor), java.nio.charset.StandardCharsets.UTF_8);
      String[] parts = decoded.split(",");
      List<Object> list = new java.util.ArrayList<>();
      for (String part : parts) {
        list.add(part);
      }
      return list;
    } catch (Exception e) {
      return List.of(cursor);
    }
  }
}