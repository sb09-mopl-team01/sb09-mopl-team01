package io.mopl.domain.contentroomchat.realtime;

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
    prefix = "mopl.content-room-chat.realtime.redis",
    name = "enabled",
    havingValue = "true"
)
public class RedisContentRoomChatRealtimePublisher implements ContentRoomChatRealtimePublisher {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;

  @Value("${mopl.content-room-chat.realtime.redis.channel:content-room-chat:realtime}")
  private String channel;

  @Override
  public void publish(ContentRoomChatRealtimeEvent event) {
    try {
      redisTemplate.convertAndSend(channel, objectMapper.writeValueAsString(event));
    } catch (JsonProcessingException | RuntimeException e) {
      log.error("Failed to publish content room chat realtime event. contentId={}", event.contentId(), e);
      throw new IllegalStateException("콘텐츠 채팅 실시간 메시지를 발행할 수 없습니다.", e);
    }
  }
}
