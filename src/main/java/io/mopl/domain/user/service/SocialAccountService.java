package io.mopl.domain.user.service;

import io.mopl.domain.user.entity.SocialAccount;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.SocialAccountRepository;
import io.mopl.domain.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialAccountService {

  private final SocialAccountRepository socialAccountRepository;
  private final UserRepository userRepository;

  @Transactional
  public void linkSocialAccount(UUID userId, String provider, String providerUserId, String providerEmail) {
    User user = userRepository.findById(userId)
        .orElseThrow(IllegalArgumentException::new);

    socialAccountRepository.findByProviderAndProviderUserId(provider, providerUserId)
        .ifPresent(account -> {
          throw new IllegalStateException();
        });

    SocialAccount socialAccount = SocialAccount.builder()
        .user(user)
        .provider(provider)
        .providerUserId(providerUserId)
        .providerEmail(providerEmail)
        .build();

    socialAccountRepository.save(socialAccount);
  }
}
