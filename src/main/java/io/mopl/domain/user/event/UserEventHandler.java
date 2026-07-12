package io.mopl.domain.user.event;

import io.mopl.domain.auth.repository.RefreshTokenRepository;
import io.mopl.domain.auth.service.TempPasswordService;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventHandler {

  private final TempPasswordService tempPasswordService;
  private final StringRedisTemplate redisTemplate;
  private final RefreshTokenRepository refreshTokenRepository;

  @Value("${jwt.access-token-validity-seconds}")
  private long accessTokenValiditySeconds;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handlePasswordChangedEvent(UserPasswordChangeEvent event) {
    try {
      tempPasswordService.deleteTempPassword(event.email());
      log.debug("Redis TempPassword deleted successfully for email={}", event.email());
    } catch (Exception e) {
      log.warn("Redis TempPassword deletion failed after DB commit. email={}", event.email(), e);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleUserLockedEvent(UserLockedEvent event) {
    try {
      redisTemplate.opsForValue().set(
          "blacklist:access_token:" + event.userId(),
          "true",
          Duration.ofSeconds(accessTokenValiditySeconds)
      );
      refreshTokenRepository.deleteByEmail(event.email());
      log.debug("Redis Lock status updated successfully for user={}", event.email());
    } catch (Exception e) {
      log.error("Redis Lock status update failed for user={}", event.email(), e);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleUserUnlockedEvent(UserUnlockedEvent event) {
    try {
      redisTemplate.delete("blacklist:access_token:" + event.userId());
      log.debug("Redis Unlock status updated successfully for userId={}", event.userId());
    } catch (Exception e) {
      log.error("Redis Unlock status update failed for userId={}", event.userId(), e);
    }
  }
}