package io.mopl.domain.user.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.mopl.domain.user.entity.SocialAccount;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.SocialAccountRepository;
import io.mopl.domain.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SocialAccountServiceTest {

  @InjectMocks
  private SocialAccountService socialAccountService;

  @Mock
  private SocialAccountRepository socialAccountRepository;

  @Mock
  private UserRepository userRepository;

  @Test
  @DisplayName("소셜 계정 연동 성공 - 정상적으로 save가 호출되어야 한다")
  void linkSocialAccount_Success() {
    UUID userId = UUID.randomUUID();
    User mockUser = User.builder().email("test@test.com").build();
    ReflectionTestUtils.setField(mockUser, "id", userId);

    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(socialAccountRepository.findByProviderAndProviderUserId("google", "12345"))
        .thenReturn(Optional.empty());

    socialAccountService.linkSocialAccount(userId, "google", "12345", "test@test.com");

    verify(socialAccountRepository, times(1)).save(any(SocialAccount.class));
  }

  @Test
  @DisplayName("소셜 계정 연동 실패 - 이미 연동된 계정이면 IllegalStateException 발생")
  void linkSocialAccount_Fail_AlreadyLinked() {
    UUID userId = UUID.randomUUID();
    User mockUser = User.builder().email("test@test.com").build();
    ReflectionTestUtils.setField(mockUser, "id", userId);
    SocialAccount existingAccount = SocialAccount.builder().build();

    when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
    when(socialAccountRepository.findByProviderAndProviderUserId("google", "12345"))
        .thenReturn(Optional.of(existingAccount));

    assertThrows(IllegalStateException.class, () -> {
      socialAccountService.linkSocialAccount(userId, "google", "12345", "test@test.com");
    });

    verify(socialAccountRepository, never()).save(any());
  }
}
