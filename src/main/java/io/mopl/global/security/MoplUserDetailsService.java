package io.mopl.global.security;

import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MoplUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;


  // Username = email로 검색
  @Override
  public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다. email: " + email));

    return new MoplUserDetails(user);
  }
}
