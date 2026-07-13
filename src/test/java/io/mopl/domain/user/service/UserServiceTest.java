package io.mopl.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.mopl.domain.auth.repository.RefreshTokenRepository;
import io.mopl.domain.auth.service.TempPasswordService;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.request.ChangePasswordRequest;
import io.mopl.domain.user.dto.request.UserCreateRequest;
import io.mopl.domain.user.dto.request.UserLockUpdateRequest;
import io.mopl.domain.user.dto.request.UserRoleUpdateRequest;
import io.mopl.domain.user.dto.request.UserUpdateRequest;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.exception.DuplicateUserEmailException;
import io.mopl.domain.user.exception.UserNotFoundException;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import java.time.Duration;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @InjectMocks
  private UserService userService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserMapper userMapper;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private TempPasswordService tempPasswordService;

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private RefreshTokenRepository refreshTokenRepository;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @Mock
  private DomainEventPublisher eventPublisher;

  @Test
  @DisplayName("회원가입 성공")
  void createUser_Success() {
    UserCreateRequest request = new UserCreateRequest("test@example.com", "홍길동", "password123");
    User user = User.builder()
        .email(request.email())
        .passwordHash("encoded_password")
        .name(request.name())
        .build();

    UserDto userDto = UserDto.builder()
        .id(UUID.randomUUID())
        .email(request.email())
        .name(request.name())
        .profileImageUrl(null)
        .role(Role.USER)
        .locked(false)
        .createdAt(Instant.now())
        .build();

    given(userRepository.existsByEmail(request.email())).willReturn(false);
    given(passwordEncoder.encode(request.password())).willReturn("encoded_password");
    given(userRepository.save(any(User.class))).willReturn(user);
    given(userMapper.toDto(user)).willReturn(userDto);

    UserDto result = userService.createUser(request);

    assertThat(result.email()).isEqualTo(request.email());
    assertThat(result.name()).isEqualTo(request.name());

    verify(userRepository).save(any(User.class));
  }

  @Test
  @DisplayName("회원가입 실패: 이미 존재하는 이메일")
  void createUser_Fail_DuplicateEmail() {
    UserCreateRequest request = new UserCreateRequest("duplicate@example.com", "홍길동", "password123");
    given(userRepository.existsByEmail(request.email())).willReturn(true);

    DuplicateUserEmailException exception = assertThrows(DuplicateUserEmailException.class, () -> userService.createUser(request));
    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_DUPLICATION);
  }

  @Test
  @DisplayName("유저 단건 조회 성공")
  void findUser_Success() {
    UUID userId = UUID.randomUUID();
    User user = mock(User.class);

    UserDto userDto = UserDto.builder()
        .id(userId)
        .email("test@example.com")
        .name("홍길동")
        .build();

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(userMapper.toDto(user)).willReturn(userDto);

    UserDto result = userService.findUser(userId);

    assertThat(result).isNotNull();
    assertThat(result.id()).isEqualTo(userId);
  }

  @Test
  @DisplayName("유저 단건 조회 실패: 존재하지 않는 유저 ID")
  void findUser_Fail_UserNotFound() {
    UUID userId = UUID.randomUUID();
    given(userRepository.findById(userId)).willReturn(Optional.empty());

    UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> userService.findUser(userId));
    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("순수 프로필 정보 업데이트 성공 (S3 제외)")
  void updateProfileInfo_Success() {
    UUID userId = UUID.randomUUID();
    UserUpdateRequest request = new UserUpdateRequest("새로운이름");
    String newImageUrl = "https://s3.amazonaws.com/new-image.jpg";
    User user = mock(User.class);

    UserDto updatedUserDto = UserDto.builder()
        .id(userId)
        .name(request.name())
        .profileImageUrl(newImageUrl)
        .build();

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(userMapper.toDto(user)).willReturn(updatedUserDto);

    UserDto result = userService.updateProfileInfo(userId, request, newImageUrl);

    assertThat(result.name()).isEqualTo("새로운이름");
    assertThat(result.profileImageUrl()).isEqualTo(newImageUrl);

    verify(user).updateProfile(request.name(), newImageUrl);
  }
  @Test
  @DisplayName("권한 변경 성공")
  void updateUserRole_Success() {
    UUID userId = UUID.randomUUID();
    UserRoleUpdateRequest request = new UserRoleUpdateRequest(Role.ADMIN);
    User user = mock(User.class);

    given(user.getId()).willReturn(userId);
    given(user.getRole()).willReturn(Role.USER);
    given(userRepository.findById(userId)).willReturn(Optional.of(user));

    userService.updateUserRole(userId, request);
    verify(user).updateRole(request.role());

    verify(eventPublisher).publish(any());
  }

  @Test
  @DisplayName("비밀번호 변경 성공")
  void changePassword_Success() {
    TransactionSynchronizationManager.initSynchronization();

    try {
      UUID userId = UUID.randomUUID();
      ChangePasswordRequest request = new ChangePasswordRequest("newPassword123!");
      User user = mock(User.class);

      given(user.getEmail()).willReturn("test@example.com");
      given(userRepository.findById(userId)).willReturn(Optional.of(user));
      given(passwordEncoder.encode(request.password())).willReturn("new_encoded_password");

      userService.changePassword(userId, request);

      verify(user).changePassword("new_encoded_password");

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(sync -> sync.afterCommit());

      verify(tempPasswordService).deleteTempPassword("test@example.com");

    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("계정 잠금 설정 성공")
  void updateUserLockStatus_Lock_Success() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      UUID userId = UUID.randomUUID();
      UserLockUpdateRequest request = new UserLockUpdateRequest(true);
      User user = mock(User.class);

      given(user.getId()).willReturn(userId);
      given(user.getEmail()).willReturn("test@example.com");
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      given(redisTemplate.opsForValue()).willReturn(valueOperations);

      userService.updateUserLockStatus(userId, request);

      verify(user).lockAccount();

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit);

      verify(valueOperations).set(eq("blacklist:access_token:" + userId), eq("true"), any(Duration.class));
      verify(refreshTokenRepository).deleteByEmail("test@example.com");

    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("계정 잠금 해제 성공")
  void updateUserLockStatus_Unlock_Success() {
    TransactionSynchronizationManager.initSynchronization();
    try {
      UUID userId = UUID.randomUUID();
      UserLockUpdateRequest request = new UserLockUpdateRequest(false);
      User user = mock(User.class);
      given(user.getId()).willReturn(userId);
      given(user.getEmail()).willReturn("test@example.com");
      given(userRepository.findById(userId)).willReturn(Optional.of(user));

      userService.updateUserLockStatus(userId, request);

      verify(user).unlockAccount();

      TransactionSynchronizationManager.getSynchronizations()
          .forEach(TransactionSynchronization::afterCommit);

      verify(redisTemplate).delete("blacklist:access_token:" + userId);

    } finally {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  @DisplayName("목록 조회 성공")
  void findUsers_Success() {
    User user = mock(User.class);

    UserDto userDto = UserDto.builder()
        .id(UUID.randomUUID())
        .email("test@example.com")
        .name("홍길동")
        .build();

    CursorResponse<User> entityResponse = new CursorResponse<>(
        List.of(user), "nextCursorValue", UUID.randomUUID(), true, 10L, "createdAt", SortDirection.DESCENDING
    );

    given(userRepository.findUsersByCursor(
        "test", "USER", false, "cursor", entityResponse.nextIdAfter(), 10, "createdAt", SortDirection.DESCENDING
    )).willReturn(entityResponse);

    given(userMapper.toDto(user)).willReturn(userDto);

    CursorResponse<UserDto> result = userService.findUsers(
        "test", "USER", false, "cursor", entityResponse.nextIdAfter(), 10, "createdAt", SortDirection.DESCENDING
    );

    assertThat(result.data()).hasSize(1);
    assertThat(result.data().get(0).name()).isEqualTo("홍길동");
    assertThat(result.hasNext()).isTrue();
    assertThat(result.totalCount()).isEqualTo(10L);
  }
}
