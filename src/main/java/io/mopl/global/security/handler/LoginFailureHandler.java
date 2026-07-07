package io.mopl.global.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {

  private final ObjectMapper objectMapper;

  @Override
  public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException exception) throws IOException, ServletException {

    log.warn("LoginFailureHandler Login Fail - {}", exception.getMessage());

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");

    Map<String, String> errorResponse = new HashMap<>();
    if (exception instanceof LockedException) {
      errorResponse.put("code", "ACCOUNT_LOCKED");
      errorResponse.put("message", "계정이 잠금 처리되었습니다.");
    } else {
      errorResponse.put("code", "LOGIN_FAILED");
      errorResponse.put("message", "아이디 또는 비밀번호가 일치하지 않습니다.");
    }

    objectMapper.writeValue(response.getWriter(), errorResponse);
  }
}
