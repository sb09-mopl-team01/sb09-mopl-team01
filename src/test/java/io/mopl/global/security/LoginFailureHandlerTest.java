package io.mopl.global.security;

import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.handler.LoginFailureHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;

import java.io.PrintWriter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginFailureHandlerTest {

  @InjectMocks
  private LoginFailureHandler loginFailureHandler;

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private PrintWriter printWriter;

  @Test
  @DisplayName("일반적인 로그인 실패 시 401 상태코드와 LOGIN_FAILED 에러 메시지를 응답한다")
  void onAuthenticationFailure_BadCredentials() throws Exception {
    BadCredentialsException exception = new BadCredentialsException("Bad credentials");
    when(response.getWriter()).thenReturn(printWriter);

    loginFailureHandler.onAuthenticationFailure(request, response, exception);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(response).setContentType("application/json;charset=UTF-8");

    String expectedJson = String.format(
        "{\"status\": 401, \"code\": \"%s\", \"message\": \"%s\"}",
        ErrorCode.LOGIN_FAILED.toString(), ErrorCode.LOGIN_FAILED.getMessage()
    );
    verify(printWriter).write(expectedJson);
  }

  @Test
  @DisplayName("잠긴 계정으로 로그인 시도 시 ACCOUNT_LOCKED 에러 메시지를 응답한다")
  void onAuthenticationFailure_LockedAccount() throws Exception {
    LockedException exception = new LockedException("Account is locked");
    when(response.getWriter()).thenReturn(printWriter);

    loginFailureHandler.onAuthenticationFailure(request, response, exception);

    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    verify(response).setContentType("application/json;charset=UTF-8");

    String expectedJson = String.format(
        "{\"status\": 401, \"code\": \"%s\", \"message\": \"%s\"}",
        ErrorCode.ACCOUNT_LOCKED.toString(), ErrorCode.ACCOUNT_LOCKED.getMessage()
    );
    verify(printWriter).write(expectedJson);
  }
}
