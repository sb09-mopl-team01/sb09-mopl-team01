package io.mopl.domain.auth.repository;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class RefreshTokenMemoryRepository {

  private final Map<String, String> tokenStorage = new ConcurrentHashMap<>();

  public void save(String email, String refreshToken) {
    tokenStorage.put(email, refreshToken);
  }

  public Optional<String> findByEmail(String email) {
    return Optional.ofNullable(tokenStorage.get(email));
  }

  public void deleteByEmail(String email) {
    tokenStorage.remove(email);
  }
}
