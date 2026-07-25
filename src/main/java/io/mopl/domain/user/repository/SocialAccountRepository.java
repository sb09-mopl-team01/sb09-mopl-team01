package io.mopl.domain.user.repository;

import io.mopl.domain.user.entity.SocialAccount;
import io.mopl.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {
  @EntityGraph(attributePaths = "user")
  Optional<SocialAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

  boolean existsByUser(User user);
}
