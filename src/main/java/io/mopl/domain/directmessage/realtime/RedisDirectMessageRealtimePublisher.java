package io.mopl.domain.directmessage.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    prefix = "mopl.direct-message.realtime.redis",
    name = "enabled",
    havingValue = "true"
)
public class RedisDirectMessageRealtimePublisher implements DirectMessageRealtimePublisher {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Value("${mopl.direct-message.realtime.redis.channel:direct-message:realtime}")
  private String channel;

  @Override
  public void publish(DirectMessageRealtimeEvent event) {
    try {
      redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(event));
    } catch (JsonProcessingException | RuntimeException e) {
      log.error("Failed to publish direct message realtime event. conversationId={}",
          event.conversationId(), e);
      throw new IllegalStateException("DM 실시간 메시지를 발행할 수 없습니다.", e);
    }
  }
}
