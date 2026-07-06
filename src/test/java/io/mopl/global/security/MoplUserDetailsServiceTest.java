package io.mopl.global.security;

import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoplUserDetailsServiceTest {

  @InjectMocks
  private MoplUserDetailsService userDetailsService;

  @Mock
  private UserRepository userRepository;

  @Test
  @DisplayName("존재하는 이메일로 조회 시 UserDetails 반환 성공")
  void loadUserByUsername_Success() {
    String email = "test@example.com";
    User mockUser = User.builder().email(email).passwordHash("encoded-pw").build();

    when(userRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));

    UserDetails userDetails = userDetailsService.loadUserByUsername(email);

    assertNotNull(userDetails);
    assertEquals(email, userDetails.getUsername());
    assertEquals("encoded-pw", userDetails.getPassword());
  }

  @Test
  @DisplayName("존재하지 않는 이메일로 조회 시 예외 발생")
  void loadUserByUsername_NotFound_ThrowsException() {
    String email = "notfound@example.com";
    when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

    UsernameNotFoundException exception = assertThrows(UsernameNotFoundException.class,
        () -> userDetailsService.loadUserByUsername(email));

    assertTrue(exception.getMessage().contains(email));
  }
}
