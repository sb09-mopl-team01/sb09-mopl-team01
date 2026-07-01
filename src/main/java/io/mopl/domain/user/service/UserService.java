package io.mopl.domain.user.service;

import io.mopl.domain.auth.service.TempPasswordService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final UserMapper userMapper;
  private final PasswordEncoder passwordEncoder;
  private final TempPasswordService tempPasswordService;

  @Transactional
  public UserDto createUser(UserCreateRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      log.info("[사용자 관리] 사용자 생성 실패. 이메일 중복. email={}", request.email());
      throw new DuplicateUserEmailException();
    }

    User user = User.builder()
        .email(request.email())
        .passwordHash(passwordEncoder.encode(request.password()))
        .name(request.name())
        .build();

    User savedUser = userRepository.save(user);
    log.info("[사용자 관리] 사용자 생성 완료. id={}", savedUser.getId());
    return userMapper.toDto(savedUser);
  }

  public UserDto findUser(UUID userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> {
          log.info("[사용자 관리] 사용자 조회 실패. id={}", userId);
          return new UserNotFoundException();
        });
    log.info("[사용자 관리] 사용자 조회 완료. id={}", userId);
    return userMapper.toDto(user);
  }

  @Transactional
  public UserDto updateProfile(UUID userId, UserUpdateRequest request, MultipartFile image) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> {
          log.info("[사용자 관리] 사용자 프로필 수정 실패.존재하지 않는 사용자. id={}", userId);
          return new UserNotFoundException();
        });

    String profileImageUrl = null;

    if (image != null && !image.isEmpty()) {
      // 이미지 로컬 저장 기능 구현 후 추가
    }

    user.updateProfile(request.name(), profileImageUrl);
    log.info("[사용자 관리] 사용자 프로필 수정 완료. id={}", userId);
    return userMapper.toDto(user);
  }

  @Transactional
  public void updateUserRole(UUID userId, UserRoleUpdateRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> {
          log.info("[사용자 관리] 사용자 권한 수정 실패.존재하지 않는 사용자. id={}", userId);
          return new UserNotFoundException();
        });
    user.updateRole(request.role());
    log.info("[사용자 관리] 사용자 권한 수정 완료. id={}, role={}", userId, user.getRole());
  }

  @Transactional
  public void changePassword(UUID userId, ChangePasswordRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> {
          log.info("[사용자 관리] 사용자 비밀번호 변경 실패.존재하지 않는 사용자. id={}", userId);
          return new UserNotFoundException();
        });

    String newPasswordHash = passwordEncoder.encode(request.password());
    user.changePassword(newPasswordHash);
    tempPasswordService.deleteTempPassword(user.getEmail());
    log.info("[사용자 관리] 사용자 비밀번호 완료. id={}", userId);
  }

  @Transactional
  public void updateUserLockStatus(UUID userId, UserLockUpdateRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> {
          log.info("[사용자 관리] 사용자 잠금 실패.존재하지 않는 사용자. id={}", userId);
          return new UserNotFoundException();
        });

    if (request.locked()) {
      user.lockAccount();
    } else {
      user.unlockAccount();
    }

    log.info("[사용자 관리] 사용자 잠금 완료. id={}", userId);
  }

  public CursorResponse<UserDto> findUsers(
      String emailLike, String roleEqual, Boolean isLocked,
      String cursor, UUID idAfter, int limit,
      String sortBy, SortDirection sortDirection) {
    log.info("[사용자 관리] 사용자 다건 조회 시작. emailLike={}, roleEqual={}, isLocked={}"
        + ", idAfter={}, limit={}, sortBy={}, sortDirection={}", emailLike, roleEqual, isLocked
    , idAfter, limit, sortBy, sortDirection);
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
