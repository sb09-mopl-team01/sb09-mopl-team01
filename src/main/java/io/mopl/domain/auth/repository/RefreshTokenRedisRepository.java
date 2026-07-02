package io.mopl.domain.auth.repository;

import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Primary
@Repository
@RequiredArgsConstructor
public class RefreshTokenRedisRepository implements RefreshTokenRepository {

  private final StringRedisTemplate redisTemplate;
  private static final String KEY_PREFIX = "AUTH:RT:";
  @Value("${jwt.max-active-tokens}")
  private int maxActiveTokens;

  @Value("${jwt.refresh-token-validity-seconds}")
  private long refreshTokenValiditySeconds;

  @Override
  public void save(String email, String refreshToken) {
    String key = KEY_PREFIX + email;

    redisTemplate.opsForList().rightPush(key, refreshToken);

    Long size = redisTemplate.opsForList().size(key);
    if (size != null && size > maxActiveTokens) {
      redisTemplate.opsForList().leftPop(key);
    }
    redisTemplate.expire(key, refreshTokenValiditySeconds, TimeUnit.SECONDS);
  }

  @Override
  public boolean isValid(String email, String refreshToken) {
    String key = KEY_PREFIX + email;
    List<String> tokens = redisTemplate.opsForList().range(key, 0, -1);
    return tokens != null && tokens.contains(refreshToken);
  }

  @Override
  public void deleteByEmail(String email) {
    redisTemplate.delete(KEY_PREFIX + email);
  }

  @Override
  public void removeToken(String email, String refreshToken) {
    String key = KEY_PREFIX + email;
    redisTemplate.opsForList().remove(key, 1, refreshToken);
  }
}
