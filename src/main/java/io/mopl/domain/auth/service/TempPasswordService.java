package io.mopl.domain.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TempPasswordService {

  private final RedisTemplate<String, Object> redisTemplate;
  private static final String REDIS_KEY_PREFIX = "TEMP_PW:";

  private static final String CHAR_SET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  public String generateRandomPassword() {
    log.debug("TempPassword Generate Started.");
    SecureRandom random = new SecureRandom();
    StringBuilder sb = new StringBuilder(10);
    for (int i = 0; i < 10; i++) {
      int index = random.nextInt(CHAR_SET.length());
      sb.append(CHAR_SET.charAt(index));
    }
    log.debug("TempPassword Generate Completed.");
    return sb.toString();
  }

  public void saveTempPassword(UUID userId, String tempPassword) {
    log.debug("TempPassword Save Started. userId={}", userId);
    redisTemplate.opsForValue().set(
        REDIS_KEY_PREFIX + userId,
        tempPassword,
        3,
        TimeUnit.MINUTES
    );
    log.info("TempPassword Save Completed. userId={}", userId);
  }

  public String getTempPassword(UUID userId) {
    log.debug("TempPassword Read Started. userId={}", userId);
    Object tempPassword = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + userId);
    log.debug("TempPassword Read Completed. userId={}", userId);
    return tempPassword != null ? tempPassword.toString() : null;
  }

  public void deleteTempPassword(UUID userId) {
    log.debug("TempPassword Delete Started. userId={}", userId);
    redisTemplate.delete(REDIS_KEY_PREFIX + userId);
    log.info("TempPassword Delete Completed. userId={}", userId);
  }
}
