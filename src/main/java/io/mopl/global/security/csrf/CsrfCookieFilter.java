package io.mopl.global.security.csrf;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CsrfCookieFilter extends OncePerRequestFilter {
  private final CsrfTokenRepository csrfTokenRepository;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
    if (csrfToken != null) {
      request.setAttribute("_csrf", csrfToken);
    }
    filterChain.doFilter(request, response);
  }
}