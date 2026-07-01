package io.mopl.global.security.filter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class MoplLoginFilter extends UsernamePasswordAuthenticationFilter {

  public MoplLoginFilter (AuthenticationManager authenticationManager) {
    super(authenticationManager);
    setFilterProcessesUrl("/api/auth/sign-in");
  }

  @Override
  public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

    String email = obtainUsername(request);
    String password = obtainPassword(request);

    UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(email, password);

    return this.getAuthenticationManager().authenticate(authRequest);
  }
}
