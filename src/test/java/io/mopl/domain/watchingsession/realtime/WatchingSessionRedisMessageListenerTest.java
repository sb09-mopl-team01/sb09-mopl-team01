package io.mopl.domain.watchingsession.realtime;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.content.dto.ContentSummary;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.user.dto.response.UserSummary;
import io.mopl.domain.watchingsession.dto.WatchingSessionChange;
import io.mopl.domain.watchingsession.dto.WatchingSessionChangeType;
import io.mopl.domain.watchingsession.dto.WatchingSessionDto;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class WatchingSessionRedisMessageListenerTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
  private final WatchingSessionRedisMessageListener listener =
      new WatchingSessionRedisMessageListener(objectMapper, messagingTemplate);

  @Test
  void relaysRedisMessageToLocalWebSocketSubscribers() throws Exception {
    UUID contentId = UUID.randomUUID();
    WatchingSessionChange change = new WatchingSessionChange(
        WatchingSessionChangeType.JOIN,
        new WatchingSessionDto(
            UUID.randomUUID(),
            Instant.now(),
            new UserSummary(UUID.randomUUID(), "사용자", "https://example.com/profile.png"),
            ContentSummary.builder()
                .id(contentId)
                .type(ContentType.MOVIE)
                .title("콘텐츠")
                .description("설명")
                .thumbnailUrl("https://example.com/thumbnail.png")
                .tags(Set.of("tag"))
                .averageRating(4.5)
                .reviewCount(10)
                .build()
        ),
        1L
    );
    byte[] payload = objectMapper.writeValueAsBytes(change);

    listener.onMessage(new DefaultMessage("watching-session:changes".getBytes(StandardCharsets.UTF_8), payload), null);

    verify(messagingTemplate).convertAndSend(eq("/sub/contents/" + contentId + "/watch"), eq(change));
  }
}
