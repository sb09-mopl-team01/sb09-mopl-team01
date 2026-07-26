package io.mopl.global.security.csrf;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class StatelessCsrfTokenRepository implements CsrfTokenRepository {

  private final CookieCsrfTokenRepository delegate;

  public StatelessCsrfTokenRepository() {
    this.delegate = CookieCsrfTokenRepository.withHttpOnlyFalse();
    this.delegate.setCookieName("XSRF-TOKEN");
    this.delegate.setHeaderName("X-XSRF-TOKEN");
    this.delegate.setCookieCustomizer(cookie -> {
      cookie.secure(true);
      cookie.path("/");
      cookie.sameSite("Lax");
    });
  }

  @Override
  public CsrfToken generateToken(HttpServletRequest request) {
    return Optional.ofNullable(delegate.loadToken(request))
        .orElseGet(() -> delegate.generateToken(request));
  }

  @Override
  public void saveToken(CsrfToken token, HttpServletRequest request, HttpServletResponse response) {
    if (token != null) {
      delegate.saveToken(token, request, response);
    }
  }

  @Override
  public CsrfToken loadToken(HttpServletRequest request) {
    return delegate.loadToken(request);
  }
}