package io.mopl.domain.auth.controller;

import io.mopl.domain.auth.dto.ResetPasswordRequest;
import io.mopl.domain.auth.dto.TokenRefreshRequest;
import io.mopl.domain.auth.dto.TokenRefreshResult;
import io.mopl.domain.auth.service.AuthService;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.CookieProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

  @InjectMocks
  private AuthController authController;

  @Mock private AuthService authService;
  @Mock private CookieProvider cookieProvider;

  @Test
  @DisplayName("getCsrfToken - 204 NoContent 반환")
  void getCsrfToken_ReturnsNoContent() {
    ResponseEntity<Void> response = authController.getCsrfToken();
    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
  }

  @Test
  @DisplayName("refresh - 성공 시 쿠키와 새로운 토큰 반환")
  void refresh_Success_ReturnsNewTokensAndCookie() {
    String currentRefreshToken = "old-token";
    UserDto mockUserDto = mock(UserDto.class);
    TokenRefreshResult mockResult = new TokenRefreshResult("new-access", "new-refresh", mockUserDto);
    ResponseCookie mockAccessCookie = ResponseCookie.from("ACCESS_TOKEN", "new-access").build();
    ResponseCookie mockRefreshCookie = ResponseCookie.from("REFRESH_TOKEN", "new-refresh").build();

    when(authService.refreshTokens(currentRefreshToken)).thenReturn(mockResult);
    when(cookieProvider.createAccessTokenCookie("new-access")).thenReturn(mockAccessCookie);
    when(cookieProvider.createRefreshTokenCookie("new-refresh")).thenReturn(mockRefreshCookie);

    ResponseEntity<?> response = authController.refresh(currentRefreshToken);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(response.getHeaders().containsKey(HttpHeaders.SET_COOKIE));
    assertTrue(response.getHeaders().get(HttpHeaders.SET_COOKIE).contains(mockAccessCookie.toString()));
    assertTrue(response.getHeaders().get(HttpHeaders.SET_COOKIE).contains(mockRefreshCookie.toString()));

    TokenRefreshRequest body = (TokenRefreshRequest) response.getBody();
    assertNotNull(body);
    assertEquals("new-access", body.accessToken());
  }

  @Test
  @DisplayName("refresh - 실패 시 401 Unauthorized 및 에러 메시지 반환")
  void refresh_Fail_ThrowsException() {
    String currentRefreshToken = "invalid-token";
    when(authService.refreshTokens(currentRefreshToken))
        .thenThrow(new BaseException(ErrorCode.INVALID_REFRESH_TOKEN));

    BaseException exception = assertThrows(BaseException.class,
        () -> authController.refresh(currentRefreshToken));

    assertEquals(ErrorCode.INVALID_REFRESH_TOKEN, exception.getErrorCode());
  }

  @Test
  @DisplayName("resetPassword - 성공 시 204 NoContent 반환")
  void resetPassword_Success() {
    ResetPasswordRequest request = new ResetPasswordRequest("test@example.com");
    doNothing().when(authService).resetPassword(request.email());

    ResponseEntity<Void> response = authController.resetPassword(request);

    assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    verify(authService).resetPassword(request.email());
  }
}
