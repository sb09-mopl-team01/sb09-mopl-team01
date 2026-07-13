package io.mopl.global.security.handler;

import io.mopl.global.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

  @Override
  public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException, ServletException {

    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType("application/json;charset=UTF-8");

    String errorCode = ErrorCode.LOGIN_FAILED.toString();
    String errorMessage = ErrorCode.LOGIN_FAILED.getMessage();

    if (exception instanceof LockedException) {
      errorCode = ErrorCode.ACCOUNT_LOCKED.toString();
      errorMessage = ErrorCode.ACCOUNT_LOCKED.getMessage();
      log.warn("Login attempt blocked for locked account");
    }

    String jsonResponse = String.format(
        "{\"status\": 401, \"code\": \"%s\", \"message\": \"%s\"}",
        errorCode, errorMessage
    );

    response.getWriter().write(jsonResponse);
  }
}
