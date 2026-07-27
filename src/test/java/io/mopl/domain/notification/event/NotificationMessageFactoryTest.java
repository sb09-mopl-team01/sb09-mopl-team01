package io.mopl.domain.notification.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.playlist.event.PlaylistSubscribedEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NotificationMessageFactoryTest {

  private final NotificationMessageFactory notificationMessageFactory =
      new NotificationMessageFactory(new ObjectMapper());

  @Test
  @DisplayName("같은 사용자가 같은 플레이리스트를 재구독하면 별도의 알림 이벤트를 생성한다")
  void playlistResubscribe_CreatesDistinctNotificationEvents() {
    UUID playlistId = UUID.randomUUID();
    UUID subscriberId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();

    NotificationRequestedEvent firstRequest = notificationMessageFactory.from(
        subscribedEvent(UUID.randomUUID(), playlistId, subscriberId, ownerId)
    ).get(0);
    NotificationRequestedEvent resubscribedRequest = notificationMessageFactory.from(
        subscribedEvent(UUID.randomUUID(), playlistId, subscriberId, ownerId)
    ).get(0);

    assertThat(resubscribedRequest.sourceEventId()).isNotEqualTo(firstRequest.sourceEventId());
  }

  private PlaylistSubscribedEvent subscribedEvent(
      UUID subscriptionId,
      UUID playlistId,
      UUID subscriberId,
      UUID ownerId
  ) {
    return new PlaylistSubscribedEvent(
        subscriptionId,
        playlistId,
        "주말 영화",
        ownerId,
        subscriberId,
        "subscriber",
        Instant.parse("2026-07-27T00:00:00Z")
    );
  }
}
