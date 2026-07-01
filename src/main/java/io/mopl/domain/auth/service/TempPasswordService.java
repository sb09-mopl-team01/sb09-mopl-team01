package io.mopl.domain.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class TempPasswordService {

  private final RedisTemplate<String, Object> redisTemplate;
  private static final String REDIS_KEY_PREFIX = "TEMP_PW:";

  private static final String CHAR_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  public String generateRandomPassword() {
    SecureRandom random = new SecureRandom();
    StringBuilder sb = new StringBuilder(10);
    for (int i = 0; i < 10; i++) {
      int index = random.nextInt(CHAR_SET.length());
      sb.append(CHAR_SET.charAt(index));
    }
    return sb.toString();
  }

  public void saveTempPassword(String email, String tempPassword) {
    redisTemplate.opsForValue().set(
        REDIS_KEY_PREFIX + email,
        tempPassword,
        3,
        TimeUnit.MINUTES
    );
  }
}
