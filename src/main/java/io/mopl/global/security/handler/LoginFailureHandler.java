package io.mopl.global.security.handler;

import io.mopl.global.exception.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationServiceException;
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

    int statusCode = HttpServletResponse.SC_UNAUTHORIZED;
    String errorCode = ErrorCode.LOGIN_FAILED.toString();
    String errorMessage = ErrorCode.LOGIN_FAILED.getMessage();

    if (exception instanceof LockedException) {
      errorCode = ErrorCode.ACCOUNT_LOCKED.toString();
      errorMessage = ErrorCode.ACCOUNT_LOCKED.getMessage();
      log.warn("Login attempt blocked for locked account");
    }
    else if (exception instanceof AuthenticationServiceException && "SOCIAL_USER".equals(exception.getMessage())) {
      statusCode = ErrorCode.SOCIAL_USER_MUST_USE_OAUTH.getStatus().value();
      errorCode = ErrorCode.SOCIAL_USER_MUST_USE_OAUTH.getCode();
      errorMessage = ErrorCode.SOCIAL_USER_MUST_USE_OAUTH.getMessage();
      log.warn("Social user tried to login via form login");
    }

    response.setStatus(statusCode);
    response.setContentType("application/json;charset=UTF-8");

    String jsonResponse = String.format(
        "{\"status\": %d, \"code\": \"%s\", \"message\": \"%s\"}",
        statusCode, errorCode, errorMessage
    );

    response.getWriter().write(jsonResponse);
  }
}