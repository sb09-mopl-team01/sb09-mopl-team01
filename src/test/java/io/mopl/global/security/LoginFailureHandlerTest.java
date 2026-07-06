package io.mopl.global.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.global.security.handler.LoginFailureHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;

import java.io.PrintWriter;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginFailureHandlerTest {

  @InjectMocks
  private LoginFailureHandler loginFailureHandler;

  @Mock private ObjectMapper objectMapper;
  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private PrintWriter printWriter;

  @Test
  @DisplayName("로그인 실패 시 401 상태코드와 에러 메시지를 응답한다")
  void onAuthenticationFailure_Success() throws Exception {
    AuthenticationException exception = mock(AuthenticationException.class);
    when(exception.getMessage()).thenReturn("Bad credentials");
    when(response.getWriter()).thenReturn(printWriter);

    loginFailureHandler.onAuthenticationFailure(request, response, exception);

    verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
    verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
    verify(response).setCharacterEncoding("UTF-8");
    verify(objectMapper).writeValue(eq(printWriter), any(Map.class));
  }
}
