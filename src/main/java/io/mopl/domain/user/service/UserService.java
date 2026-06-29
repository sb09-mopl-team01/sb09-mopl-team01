package io.mopl.domain.user.service;

import io.mopl.domain.user.exception.DuplicateUserEmailException;
import io.mopl.domain.user.exception.UserNotFoundException;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.request.ChangePasswordRequest;
import io.mopl.domain.user.dto.request.UserCreateRequest;
import io.mopl.domain.user.dto.request.UserLockUpdateRequest;
import io.mopl.domain.user.dto.request.UserRoleUpdateRequest;
import io.mopl.domain.user.dto.request.UserUpdateRequest;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;

  @Transactional
  public UserDto createUser(UserCreateRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new DuplicateUserEmailException();
    }

    User user = User.builder()
        .email(request.email())
        .passwordHash(passwordEncoder.encode(request.password()))
        .name(request.name())
        .build();

    User savedUser = userRepository.save(user);
    return userMapper.toDto(savedUser);
  }

  public UserDto findUser(UUID userId) {
    User user = getUserById(userId);
    return userMapper.toDto(user);
  }

  @Transactional
  public UserDto updateProfile(UUID userId, UserUpdateRequest request, MultipartFile image) {
    User user = getUserById(userId);

    String profileImageUrl = null;

    if (image != null && !image.isEmpty()) {
      // 이미지 로컬 저장 기능 구현 후 추가
    }

    user.updateProfile(request.name(), profileImageUrl);

    return userMapper.toDto(user);
  }

  @Transactional
  public void updateUserRole(UUID userId, UserRoleUpdateRequest request) {
    User user = getUserById(userId);

    user.updateRole(request.role());
  }

  @Transactional
  public void changePassword(UUID userId, ChangePasswordRequest request) {
    User user = getUserById(userId);

    String newPasswordHash = passwordEncoder.encode(request.password());
    user.changePassword(newPasswordHash);
  }

  @Transactional
  public void updateUserLockStatus(UUID userId, UserLockUpdateRequest request) {
    User user = getUserById(userId);

    if (request.locked()) {
      user.lockAccount();
    } else {
      user.unlockAccount();
    }
  }

  public CursorResponse<UserDto> findUsers(
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

  private User getUserById(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(UserNotFoundException::new);
  }
}
