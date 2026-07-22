package io.mopl.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.mopl.domain.user.document.UserDocument;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.request.ChangePasswordRequest;
import io.mopl.domain.user.dto.request.UserCreateRequest;
import io.mopl.domain.user.dto.request.UserLockUpdateRequest;
import io.mopl.domain.user.dto.request.UserRoleUpdateRequest;
import io.mopl.domain.user.dto.request.UserUpdateRequest;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.event.UserLockedEvent;
import io.mopl.domain.user.event.UserPasswordChangeEvent;
import io.mopl.domain.user.event.UserRoleChangedEvent;
import io.mopl.domain.user.event.UserUnlockedEvent;
import io.mopl.domain.user.exception.DuplicateUserEmailException;
import io.mopl.domain.user.exception.UserNotFoundException;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.domain.user.repository.search.UserSearchRepository;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.OpenSearchCursorResponse;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @InjectMocks
  private UserService userService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserSearchRepository userSearchRepository;

  @Mock
  private UserMapper userMapper;

  @Mock
  private PasswordEncoder passwordEncoder;

  @Mock
  private DomainEventPublisher eventPublisher;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(userService, "userSearchRepository", userSearchRepository);
  }

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
    verify(eventPublisher).publish(any(io.mopl.domain.user.event.UserSyncedEvent.class));
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
  @DisplayName("프로필 수정 성공")
  void updateProfile_Success() {
    UUID userId = UUID.randomUUID();
    UserUpdateRequest request = new UserUpdateRequest("새로운이름");
    User user = mock(User.class);

    UserDto updatedUserDto = UserDto.builder()
        .id(userId)
        .name(request.name())
        .build();

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(user.getId()).willReturn(userId);
    given(user.getName()).willReturn("새로운이름");
    given(user.getEmail()).willReturn("test@example.com");
    given(user.getRole()).willReturn(Role.USER);
    given(user.getCreatedAt()).willReturn(Instant.now());
    given(userMapper.toDto(user)).willReturn(updatedUserDto);

    UserDto result = userService.updateProfileInfo(userId, request, null);

    assertThat(result.name()).isEqualTo("새로운이름");
    verify(user).updateProfile(request.name(), null);
    verify(eventPublisher).publish(any(io.mopl.domain.user.event.UserSyncedEvent.class));
  }

  @Test
  @DisplayName("권한 변경 성공 및 이벤트 발행 검증")
  void updateUserRole_Success() {
    UUID userId = UUID.randomUUID();
    UserRoleUpdateRequest request = new UserRoleUpdateRequest(Role.ADMIN);
    User user = mock(User.class);

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(user.getRole()).willReturn(Role.USER);
    given(user.getId()).willReturn(userId);
    given(user.getName()).willReturn("홍길동");
    given(user.getEmail()).willReturn("test@example.com");
    given(user.getCreatedAt()).willReturn(Instant.now());

    userService.updateUserRole(userId, request);

    verify(user).updateRole(request.role());
    verify(eventPublisher).publish(any(UserRoleChangedEvent.class));
  }

  @Test
  @DisplayName("비밀번호 변경 성공 및 이벤트 발행 검증")
  void changePassword_Success() {
    UUID userId = UUID.randomUUID();
    ChangePasswordRequest request = new ChangePasswordRequest("newPassword123!");
    User user = mock(User.class);

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(passwordEncoder.encode(request.password())).willReturn("new_encoded_password");
    given(user.getId()).willReturn(userId);

    userService.changePassword(userId, request);

    verify(user).changePassword("new_encoded_password");
    verify(eventPublisher).publish(any(UserPasswordChangeEvent.class));
  }

  @Test
  @DisplayName("계정 잠금 설정 성공 및 이벤트 발행 검증")
  void updateUserLockStatus_Lock_Success() {
    UUID userId = UUID.randomUUID();
    UserLockUpdateRequest request = new UserLockUpdateRequest(true);
    User user = mock(User.class);

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(user.getId()).willReturn(userId);
    given(user.getRole()).willReturn(Role.USER);
    given(user.getName()).willReturn("홍길동");
    given(user.getEmail()).willReturn("test@example.com");
    given(user.getCreatedAt()).willReturn(Instant.now());

    userService.updateUserLockStatus(userId, request);

    verify(user).lockAccount();
    verify(eventPublisher).publish(any(UserLockedEvent.class));
  }

  @Test
  @DisplayName("계정 잠금 해제 성공 및 이벤트 발행 검증")
  void updateUserLockStatus_Unlock_Success() {
    UUID userId = UUID.randomUUID();
    UserLockUpdateRequest request = new UserLockUpdateRequest(false);
    User user = mock(User.class);

    given(userRepository.findById(userId)).willReturn(Optional.of(user));
    given(user.getId()).willReturn(userId);
    given(user.getRole()).willReturn(Role.USER);
    given(user.getName()).willReturn("홍길동");
    given(user.getEmail()).willReturn("test@example.com");
    given(user.getCreatedAt()).willReturn(Instant.now());

    userService.updateUserLockStatus(userId, request);

    verify(user).unlockAccount();
    verify(eventPublisher).publish(any(UserUnlockedEvent.class));
  }

  @Test
  @DisplayName("목록 조회 성공: OpenSearch 검색 경로 (emailLike 존재 시)")
  void findUsers_Success_OpenSearch() {
    UUID userId = UUID.randomUUID();
    UserDocument userDocument = UserDocument.builder()
        .id(userId)
        .name("홍길동")
        .email("test@example.com")
        .build();

    User user = mock(User.class);
    UserDto userDto = UserDto.builder()
        .id(userId)
        .email("test@example.com")
        .name("홍길동")
        .build();

    OpenSearchCursorResponse<UserDocument> openSearchResponse = new OpenSearchCursorResponse<>(
        List.of(userDocument), List.of("sortVal", userId.toString()), true, 10L
    );

    given(userSearchRepository.searchUsersByCursor(
        eq("test"), eq("USER"), eq(false), any(), eq(10), eq("createdAt"), eq(SortDirection.DESCENDING)
    )).willReturn(openSearchResponse);

    given(userRepository.findAllByIdIn(List.of(userId))).willReturn(List.of(user));
    given(user.getId()).willReturn(userId);
    given(userMapper.toDto(user)).willReturn(userDto);

    CursorResponse<UserDto> result = userService.findUsers(
        "test", "USER", false, null, null, 10, "createdAt", SortDirection.DESCENDING
    );

    assertThat(result.data()).hasSize(1);
    assertThat(result.data().get(0).name()).isEqualTo("홍길동");
    assertThat(result.hasNext()).isTrue();
    assertThat(result.totalCount()).isEqualTo(10L);
    verify(userSearchRepository).searchUsersByCursor(any(), any(), any(), any(), anyInt(), any(), any());
  }

  @Test
  @DisplayName("목록 조회 성공: Database 검색 경로 (emailLike 미존재 시)")
  void findUsers_Success_Database() {
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
        null, "USER", false, "cursor", entityResponse.nextIdAfter(), 10, "createdAt", SortDirection.DESCENDING
    )).willReturn(entityResponse);

    given(userMapper.toDto(user)).willReturn(userDto);

    CursorResponse<UserDto> result = userService.findUsers(
        null, "USER", false, "cursor", entityResponse.nextIdAfter(), 10, "createdAt", SortDirection.DESCENDING
    );

    assertThat(result.data()).hasSize(1);
    assertThat(result.data().get(0).name()).isEqualTo("홍길동");
    assertThat(result.hasNext()).isTrue();
    assertThat(result.totalCount()).isEqualTo(10L);
    verify(userRepository).findUsersByCursor(any(), any(), any(), any(), any(), anyInt(), any(), any());
  }
}