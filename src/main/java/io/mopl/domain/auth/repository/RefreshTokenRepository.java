package io.mopl.domain.auth.repository;

public interface RefreshTokenRepository {
  void save(String email, String refreshToken);
  boolean isValid(String email, String refreshToken);
  void deleteByEmail(String email);
  void removeToken(String email, String refreshToken);
}
