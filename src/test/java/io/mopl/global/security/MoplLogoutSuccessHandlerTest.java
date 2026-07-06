package io.mopl.global.security;


import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.mopl.global.security.handler.MoplLogoutSuccessHandler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;

@ExtendWith(MockitoExtension.class)
class MoplLogoutSuccessHandlerTest {

  @InjectMocks
  private MoplLogoutSuccessHandler moplLogoutSuccessHandler;

  @Mock
  private HttpServletRequest request;
  @Mock
  private HttpServletResponse response;
  @Mock
  private Authentication authentication;
  @Mock
  private PrintWriter printWriter;

  @Test
  @DisplayName("로그아웃 성공 시 쿠키 삭제 헤더와 200 상태코드를 반환한다")
  void onLogoutSuccess_Success() throws Exception {
    when(response.getWriter()).thenReturn(printWriter);

    moplLogoutSuccessHandler.onLogoutSuccess(request, response, authentication);

    verify(response).addHeader(eq(HttpHeaders.SET_COOKIE), contains("Max-Age=0"));
    verify(response).setStatus(HttpStatus.OK.value());
    verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
    verify(printWriter).write("{\"message\": \"로그아웃 되었습니다.\"}");
  }
}