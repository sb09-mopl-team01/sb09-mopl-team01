package io.mopl.global.security;

import io.mopl.domain.user.entity.User;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Getter
@Setter
public class MoplUserDetails implements UserDetails, OAuth2User {

  private final User user;
  private Map<String, Object> attributes;
  private boolean isTempLogin = false;

  public MoplUserDetails(User user) {
    this.user = user;
  }

  public MoplUserDetails(User user, Map<String, Object> attributes) {
    this.user = user;
    this.attributes = attributes;
  }

  @Override
  public Map<String, Object> getAttributes() {
    return attributes;
  }

  @Override
  public String getName() {
    return user.getEmail();
  }

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
  }

  // 해싱된 비밀번호 사용
  @Override
  public String getPassword() {
    return user.getPasswordHash();
  }

  // Email을 Username으로 사용
  @Override
  public String getUsername() {
    return user.getEmail();
  }

  @Override
  public boolean isAccountNonLocked() {
    return !user.isLocked();
  }


  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  @Override
  public boolean isCredentialsNonExpired() {

    return true;
  }

  @Override
  public boolean isEnabled() {

    return true;
  }
}
