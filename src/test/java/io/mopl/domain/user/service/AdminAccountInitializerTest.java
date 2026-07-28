package io.mopl.domain.user.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import io.mopl.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AdminAccountInitializerTest {

  private final UserRepository userRepository = mock(UserRepository.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final AdminAccountInitializer initializer =
      new AdminAccountInitializer(userRepository, passwordEncoder);

  @Test
  void 관리자_초기화가_활성화되어도_필수_설정이_없으면_실패한다() {
    ReflectionTestUtils.setField(initializer, "adminUsername", "");
    ReflectionTestUtils.setField(initializer, "adminEmail", "");
    ReflectionTestUtils.setField(initializer, "adminPassword", "");

    assertThatThrownBy(initializer::run)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ADMIN_USERNAME");

    verifyNoInteractions(userRepository, passwordEncoder);
  }
}
