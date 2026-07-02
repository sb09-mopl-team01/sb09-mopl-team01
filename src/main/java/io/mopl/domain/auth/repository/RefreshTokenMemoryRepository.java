package io.mopl.domain.auth.repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

//@Repository
@RequiredArgsConstructor
public class RefreshTokenMemoryRepository implements RefreshTokenRepository{

  private final Map<String, List<String>> tokenStorage = new ConcurrentHashMap<>();

  @Value("${auth.max-active-tokens}")
  private int maxActiveTokens;

  @Value("${auth.refresh-ttl-millis}")
  private long refreshTokenTtlMillis;

  public void save(String email, String refreshToken) {
    tokenStorage.compute(email, (key, tokens) -> {
      if (tokens == null) {
        tokens = Collections.synchronizedList(new ArrayList<>());
      }

      tokens.add(refreshToken);

      while (tokens.size() > maxActiveTokens) {
        tokens.remove(0);
      }
      return tokens;
    });
  }

  public boolean isValid(String email, String refreshToken) {
    List<String> tokens = tokenStorage.get(email);
    return tokens != null && tokens.contains(refreshToken);
  }

  public void deleteByEmail(String email) {
    tokenStorage.remove(email);
  }

  public void removeToken(String email, String refreshToken) {
    tokenStorage.computeIfPresent(email, (key, tokens) -> {
      tokens.remove(refreshToken);
      return tokens.isEmpty() ? null : tokens;
    });
  }
}
