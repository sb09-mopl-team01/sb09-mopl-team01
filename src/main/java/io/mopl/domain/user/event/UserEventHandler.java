package io.mopl.domain.user.event;

import io.mopl.domain.auth.repository.RefreshTokenRepository;
import io.mopl.domain.auth.service.TempPasswordService;
import io.mopl.domain.user.document.UserDocument;
import io.mopl.domain.user.repository.search.UserSearchRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
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

  @Autowired(required = false)
  private final UserSearchRepository userSearchRepository;

  @Value("${jwt.access-token-validity-seconds}")
  private long accessTokenValiditySeconds;

  @Async("userEventExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handlePasswordChangedEvent(UserPasswordChangeEvent event) { 
    try {
      tempPasswordService.deleteTempPassword(event.userId()); 
      log.debug("Redis TempPassword deleted successfully for userId={}", event.userId()); 
    } catch (Exception e) {
      log.warn("Redis TempPassword deletion failed after DB commit. userId={}", event.userId(), e); 
    }
  }

  @Async("userEventExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleUserLockedEvent(UserLockedEvent event) { 
    try {
      redisTemplate.opsForValue().set( 
          "blacklist:access_token:" + event.userId(), 
          "true", 
          Duration.ofSeconds(accessTokenValiditySeconds) 
      );
      refreshTokenRepository.deleteByUserId(event.userId()); 
      log.debug("Redis Lock status updated successfully for user={}", event.userId()); 
    } catch (Exception e) {
      log.error("Redis Lock status update failed for user={}", event.userId(), e); 
    }
  }

  @Async("userEventExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleUserUnlockedEvent(UserUnlockedEvent event) { 
    try {
      redisTemplate.delete("blacklist:access_token:" + event.userId()); 
      log.debug("Redis Unlock status updated successfully for userId={}", event.userId()); 
    } catch (Exception e) {
      log.error("Redis Unlock status update failed for userId={}", event.userId(), e); 
    }
  }

  @Async("userEventExecutor")
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleUserSync(UserSyncedEvent event) { 
    if (userSearchRepository == null) return; 
    try {
      UserDocument document = UserDocument.builder() 
          .id(event.userId()) 
          .name(event.name()) 
          .email(event.email()) 
          .role(event.role()) 
          .isLocked(event.isLocked()) 
          .createdAt(event.createdAt()) 
          .build(); 

      userSearchRepository.save(document); 
    } catch (Exception e) {
      log.error("OpenSearch sync failed. userId={}", event.userId(), e); 
    }
  }
}