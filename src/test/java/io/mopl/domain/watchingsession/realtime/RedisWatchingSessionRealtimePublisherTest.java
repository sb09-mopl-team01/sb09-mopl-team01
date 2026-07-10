package io.mopl.domain.watchingsession.realtime;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class RedisWatchingSessionRealtimePublisherTest {

  private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final RedisWatchingSessionRealtimePublisher publisher =
      new RedisWatchingSessionRealtimePublisher(redisTemplate, objectMapper);

  @Test
  void publishesSerializedChangeToConfiguredChannel() throws Exception {
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
    ReflectionTestUtils.setField(publisher, "channel", "watching-session:test");
    ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

    publisher.publish(change);

    verify(redisTemplate).convertAndSend(eq("watching-session:test"), payloadCaptor.capture());
    assertThat(objectMapper.readValue(payloadCaptor.getValue(), WatchingSessionChange.class))
        .isEqualTo(change);
  }
}
