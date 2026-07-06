package io.mopl.domain.auth.service;

import io.mopl.domain.auth.dto.TokenRefreshResult;
import io.mopl.domain.auth.repository.RefreshTokenRepository;
import io.mopl.domain.mail.service.MailService;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.exception.UserNotFoundException;
import io.mopl.domain.user.mapper.UserMapper;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.MoplUserDetailsService;
import io.mopl.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

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
  @Mock private MailService mailService;
  @Mock private PasswordEncoder passwordEncoder;

  @Test
  @DisplayName("refreshTokens - 성공")
  void refreshTokens_Success() {
    String oldRefreshToken = "old-refresh-token";
    String email = "test@example.com";
    MoplUserDetails mockUserDetails = mock(MoplUserDetails.class);

    when(jwtProvider.validateToken(oldRefreshToken)).thenReturn(true);
    when(jwtProvider.getUsername(oldRefreshToken)).thenReturn(email);
    when(refreshTokenRepository.isValid(email, oldRefreshToken)).thenReturn(true);
    when(userDetailsService.loadUserByUsername(email)).thenReturn(mockUserDetails);
    when(jwtProvider.generateAccessToken(mockUserDetails)).thenReturn("new-access-token");
    when(jwtProvider.generateRefreshToken(email)).thenReturn("new-refresh-token");
    when(userMapper.toDto(any())).thenReturn(mock(UserDto.class));

    TokenRefreshResult result = authService.refreshTokens(oldRefreshToken);

    assertNotNull(result);
    assertEquals("new-access-token", result.newAccessToken());
    assertEquals("new-refresh-token", result.newRefreshToken());
    verify(refreshTokenRepository).removeToken(email, oldRefreshToken);
    verify(refreshTokenRepository).save(email, "new-refresh-token");
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
    String email = "test@example.com";

    when(jwtProvider.validateToken(manipulatedToken)).thenReturn(true);
    when(jwtProvider.getUsername(manipulatedToken)).thenReturn(email);
    when(refreshTokenRepository.isValid(email, manipulatedToken)).thenReturn(false);

    BaseException exception = assertThrows(BaseException.class,
        () -> authService.refreshTokens(manipulatedToken));

    assertEquals(ErrorCode.EXPIRED_OR_MANIPULATED_REFRESH_TOKEN, exception.getErrorCode());
  }

  @Test
  @DisplayName("resetPassword - 성공")
  void resetPassword_Success() {
    String email = "test@example.com";
    String tempPw = "temp1234";
    String encodedPw = "encodedTemp1234";

    when(userRepository.findByEmail(email)).thenReturn(Optional.of(mock(User.class)));
    when(tempPasswordService.generateRandomPassword()).thenReturn(tempPw);
    when(passwordEncoder.encode(tempPw)).thenReturn(encodedPw);

    authService.resetPassword(email);

    verify(tempPasswordService).saveTempPassword(email, encodedPw);
    verify(mailService).sendTempPasswordEmail(email, tempPw);
  }

  @Test
  @DisplayName("resetPassword - 존재하지 않는 이메일 예외 발생")
  void resetPassword_UserNotFound_ThrowsException() {
    String email = "notfound@example.com";
    when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

    assertThrows(UserNotFoundException.class, () -> authService.resetPassword(email));
  }
}
