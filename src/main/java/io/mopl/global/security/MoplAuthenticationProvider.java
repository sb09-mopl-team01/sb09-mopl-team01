package io.mopl.global.security;

import io.mopl.domain.auth.service.TempPasswordService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MoplAuthenticationProvider implements AuthenticationProvider {

  private final UserDetailsService userDetailsService;
  private final PasswordEncoder passwordEncoder;
  private final TempPasswordService tempPasswordService;

  @Override
  public Authentication authenticate(Authentication authentication) throws AuthenticationException {
    String email = authentication.getName();
    String password = (String) authentication.getCredentials();

    MoplUserDetails userDetails = (MoplUserDetails) userDetailsService.loadUserByUsername(email);

    String tempPassword = tempPasswordService.getTempPassword(email);
    boolean isTempLogin = false;

    if (tempPassword != null && passwordEncoder.matches(password, tempPassword)) {
      isTempLogin = true;
    } else if (passwordEncoder.matches(password, userDetails.getPassword())) {
      isTempLogin = false;
    } else {
      throw new BadCredentialsException("비밀번호가 일치하지 않습니다.");
    }

    userDetails.setTempLogin(isTempLogin);

    return new UsernamePasswordAuthenticationToken(userDetails, password, userDetails.getAuthorities());
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }
}
