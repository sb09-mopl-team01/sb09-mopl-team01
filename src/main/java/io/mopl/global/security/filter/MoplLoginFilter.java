package io.mopl.global.security.filter;

import io.mopl.domain.user.repository.SocialAccountRepository;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.security.MoplUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

public class MoplLoginFilter extends UsernamePasswordAuthenticationFilter {

  private final SocialAccountRepository socialAccountRepository;

  public MoplLoginFilter(AuthenticationManager authenticationManager, SocialAccountRepository socialAccountRepository) {
    super(authenticationManager);
    this.socialAccountRepository = socialAccountRepository;
    setFilterProcessesUrl("/api/auth/sign-in");

    setUsernameParameter("username");
    setPasswordParameter("password");
  }

  @Override
  public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

    String email = obtainUsername(request);
    String password = obtainPassword(request);

    UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(email, password);

    Authentication authResult = this.getAuthenticationManager().authenticate(authRequest);

    MoplUserDetails userDetails = (MoplUserDetails) authResult.getPrincipal();

    if (!userDetails.isAccountNonLocked()) {
      throw new LockedException(ErrorCode.ACCOUNT_LOCKED.getMessage());
    }

    if (socialAccountRepository.existsByUser(userDetails.getUser())) {
      throw new AuthenticationServiceException("SOCIAL_USER");
    }

    return authResult;
  }
}