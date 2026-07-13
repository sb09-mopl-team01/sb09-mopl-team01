package io.mopl.domain.auth.repository;

import java.util.UUID;

public interface RefreshTokenRepository {
  void save(UUID userId, String refreshToken);
  boolean isValid(UUID userId, String refreshToken);
  void deleteByUserId(UUID userId);
  void removeToken(UUID userId, String refreshToken);
}
