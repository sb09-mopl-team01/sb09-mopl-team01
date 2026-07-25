package io.mopl.domain.auth.service;

import io.mopl.domain.auth.dto.TokenRefreshResult;
import io.mopl.domain.auth.repository.RefreshTokenRepository;
import io.mopl.domain.mail.event.TempPasswordIssuedEvent;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.exception.UserNotFoundException;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.MoplUserDetailsService;
import io.mopl.global.security.jwt.JwtProvider;
import io.mopl.domain.user.repository.SocialAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @InjectMocks
  private AuthService authService;

  @Mock private JwtProvider jwtProvider;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private MoplUserDetailsService userDetailsService;
  @Mock private UserMapper userMapper;
  @Mock private UserRepository userRepository;
  @Mock private TempPasswordService tempPasswordService;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private SocialAccountRepository socialAccountRepository;

  @Test
  @DisplayName("refreshTokens - 성공")
  void refreshTokens_Success() {
    String oldRefreshToken = "old-refresh-token";
    String email = "test@example.com";
    UUID userId = UUID.randomUUID();
    MoplUserDetails mockUserDetails = mock(MoplUserDetails.class);

    when(jwtProvider.validateToken(oldRefreshToken)).thenReturn(true);
    when(jwtProvider.getUsername(oldRefreshToken)).thenReturn(email);
    when(jwtProvider.getUserId(oldRefreshToken)).thenReturn(userId);
    when(refreshTokenRepository.isValid(userId, oldRefreshToken)).thenReturn(true);
    when(userDetailsService.loadUserByUsername(email)).thenReturn(mockUserDetails);

    when(mockUserDetails.isAccountNonLocked()).thenReturn(true);
    when(mockUserDetails.getUser()).thenReturn(mock(User.class));

    when(jwtProvider.generateAccessToken(mockUserDetails)).thenReturn("new-access-token");
    when(jwtProvider.generateRefreshToken(email, userId.toString())).thenReturn("new-refresh-token");
    when(userMapper.toDto(any())).thenReturn(mock(UserDto.class));

    TokenRefreshResult result = authService.refreshTokens(oldRefreshToken);

    assertNotNull(result);
    assertEquals("new-access-token", result.newAccessToken());
    assertEquals("new-refresh-token", result.newRefreshToken());
    verify(refreshTokenRepository).removeToken(userId, oldRefreshToken);
    verify(refreshTokenRepository).save(userId, "new-refresh-token");
  }

  @Test
  @DisplayName("refreshTokens - JWT 형식이 잘못되었거나 유효하지 않은 경우 예외 발생")
  void refreshTokens_InvalidToken_ThrowsException() {
    String invalidToken = "invalid-token";
    when(jwtProvider.validateToken(invalidToken)).thenReturn(false);

    BaseException exception = assertThrows(BaseException.class,
        () -> authService.refreshTokens(invalidToken));

    assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, exception.getErrorCode());
  }

  @Test
  @DisplayName("refreshTokens - 저장소에 없거나 조작된 리프레시 토큰인 경우 예외 발생")
  void refreshTokens_ExpiredOrManipulatedToken_ThrowsException() {
    String manipulatedToken = "manipulated-token";
    UUID userId = UUID.randomUUID();

    when(jwtProvider.validateToken(manipulatedToken)).thenReturn(true);
    when(jwtProvider.getUserId(manipulatedToken)).thenReturn(userId);
    when(refreshTokenRepository.isValid(userId, manipulatedToken)).thenReturn(false);

    BaseException exception = assertThrows(BaseException.class,
        () -> authService.refreshTokens(manipulatedToken));

    assertEquals(ErrorCode.EXPIRED_OR_MANIPULATED_REFRESH_TOKEN, exception.getErrorCode());
  }

  @Test
  @DisplayName("resetPassword - 성공")
  void resetPassword_Success() {
    String email = "test@example.com";
    UUID userId = UUID.randomUUID();
    String tempPw = "temp1234";
    String encodedPw = "encodedTemp1234";

    User user = mock(User.class);
    when(user.getId()).thenReturn(userId);

    when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
    when(socialAccountRepository.existsByUser(user)).thenReturn(false);
    when(tempPasswordService.generateRandomPassword()).thenReturn(tempPw);
    when(passwordEncoder.encode(tempPw)).thenReturn(encodedPw);

    authService.resetPassword(email);

    verify(tempPasswordService).saveTempPassword(userId, encodedPw);
    verify(eventPublisher).publish(any(TempPasswordIssuedEvent.class));
  }

  @Test
  @DisplayName("resetPassword - 존재하지 않는 이메일 예외 발생")
  void resetPassword_UserNotFound_ThrowsException() {
    String email = "notfound@example.com";
    when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

    assertThrows(UserNotFoundException.class, () -> authService.resetPassword(email));
  }
}
