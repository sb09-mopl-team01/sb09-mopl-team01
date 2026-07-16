package io.mopl.domain.notification.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.notification.event.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    prefix = "mopl.notification.realtime.redis",
    name = "enabled",
    havingValue = "true"
)
public class RedisNotificationPublisher implements NotificationRealtimePublisher {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Value("${mopl.notification.realtime.redis.channel:notification:realtime}")
  private String channel;

  @Override
  public void publish(NotificationCreatedEvent event) {
    try {
      redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(event));
    } catch (JsonProcessingException e) {
      log.error(
          "Failed to serialize notification realtime event. notificationId={}, receiverId={}",
          event.notificationId(),
          event.receiverId(),
          e
      );
    } catch (RuntimeException e) {
      log.error(
          "Failed to publish notification realtime event. notificationId={}, receiverId={}",
          event.notificationId(),
          event.receiverId(),
          e
      );
    }
  }
}
