package io.mopl.domain.watchingsession.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.watchingsession.dto.WatchingSessionChange;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "mopl.watching-session.redis.enabled", havingValue = "true")
public class RedisWatchingSessionRealtimePublisher implements WatchingSessionRealtimePublisher {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Value("${mopl.watching-session.redis.channel:watching-session:changes}")
  private String channel;

  @Override
  public void publish(WatchingSessionChange change) {
    try {
      redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(change));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("시청 세션 변경 이벤트를 직렬화할 수 없습니다.", e);
    }
  }
}
