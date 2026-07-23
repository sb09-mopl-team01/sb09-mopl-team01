package io.mopl.global.security.oauth.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

  @Value("${mopl.frontend.base-url}")
  private String frontendBaseUrl;

  @Override
  public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException exception) throws IOException, ServletException {

    log.error("OAuth2 Login Authentication Failure", exception);
    log.error("Failure Message: {}", exception.getMessage());

    String errorCode = "oauth_failed";

    String errorMessage = exception.getMessage() != null ? exception.getMessage() : "unknown_error";

    String redirectUrl = UriComponentsBuilder.fromUriString(frontendBaseUrl)
        .path("/#/sign-in")
        .queryParam("error", errorCode)
        .queryParam("error_message", errorMessage)
        .build()
        .toUriString();

    getRedirectStrategy().sendRedirect(request, response, redirectUrl);
  }
}
