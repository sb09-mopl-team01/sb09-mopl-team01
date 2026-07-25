package io.mopl.global.security.oauth.handler;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

  @Value("${mopl.frontend.base-url}")
  private String frontendBaseUrl;

  @Override
  public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException exception) throws IOException, ServletException {

    String errorCode = "oauth_failed";
    String errorMessage = "user_not_exists";

    if (exception.getMessage() != null && exception.getMessage().equals("user_not_exists")) {
      errorMessage = "user_not_exists";
    }

    if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
      String specificErrorCode = oauth2Exception.getError().getErrorCode();

      if ("LOCAL_ACCOUNT_ALREADY_EXISTS".equals(specificErrorCode)) {
        errorCode = "local_account_exists";
        errorMessage = "already_registered_with_email";
      }
    }

    String redirectUrl = UriComponentsBuilder.fromUriString(frontendBaseUrl)
        .path("/#/sign-in")
        .queryParam("error", errorCode)
        .queryParam("error_message", errorMessage)
        .build()
        .toUriString();

    getRedirectStrategy().sendRedirect(request, response, redirectUrl);
  }
}
