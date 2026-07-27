package io.mopl.domain.notification.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.serializers.json.JsonSchemaAndValue;
import io.mopl.domain.directmessage.event.DirectMessageSentEvent;
import io.mopl.domain.follow.event.FollowCreatedEvent;
import io.mopl.domain.notification.dto.NotificationCreateCommand;
import io.mopl.domain.notification.entity.NotificationLevel;
import io.mopl.domain.playlist.event.PlaylistContentAddedEvent;
import io.mopl.domain.playlist.event.PlaylistCreatedEvent;
import io.mopl.domain.playlist.event.PlaylistSubscribedEvent;
import io.mopl.domain.user.event.UserRoleChangedEvent;
import io.mopl.infra.kafka.IntegrationEventEnvelope;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class NotificationMessageFactory {

  private static final int NOTIFICATION_EVENT_VERSION = 1;
  private static final String DEFAULT_DISPLAY_NAME = "사용자";
  private static final String FOLLOW_TITLE = "새 팔로워가 생겼습니다";
  private static final String FOLLOW_CONTENT_FORMAT = "%s님이 회원님을 팔로우했습니다.";
  private static final String PLAYLIST_SUBSCRIBED_TITLE = "플레이리스트를 구독했습니다";
  private static final String PLAYLIST_SUBSCRIBED_CONTENT_FORMAT =
      "%s님이 '%s' 플레이리스트를 구독했습니다.";
  private static final String PLAYLIST_CONTENT_ADDED_TITLE = "구독 중인 플레이리스트에 콘텐츠가 추가되었습니다";
  private static final String PLAYLIST_CONTENT_ADDED_CONTENT_FORMAT =
      "'%s' 플레이리스트에 '%s' 콘텐츠가 추가되었습니다.";
  private static final String FOLLOWEE_ACTIVITY_TITLE = "팔로우한 사용자의 새 활동";
  private static final String FOLLOWEE_ACTIVITY_CONTENT_FORMAT = "%s님이 새 플레이리스트를 만들었습니다.";
  private static final String USER_ROLE_CHANGED_TITLE = "권한이 변경되었습니다";
  private static final String USER_ROLE_CHANGED_CONTENT_FORMAT = "회원님의 권한이 %s로 변경되었습니다.";
  private static final String DIRECT_MESSAGE_TITLE = "새 DM이 도착했습니다";
  private static final String DIRECT_MESSAGE_CONTENT_FORMAT = "%s님이 메시지를 보냈습니다.";

  private final ObjectMapper objectMapper;

  public List<NotificationRequestedEvent> from(FollowCreatedEvent event) {
    return List.of(request(event.followId(), event.followeeId(), FOLLOW_TITLE,
        FOLLOW_CONTENT_FORMAT.formatted(displayName(event.followerName()))));
  }

  public List<NotificationRequestedEvent> from(PlaylistSubscribedEvent event) {
    return List.of(request(event.subscriptionId(), event.ownerId(), PLAYLIST_SUBSCRIBED_TITLE,
        PLAYLIST_SUBSCRIBED_CONTENT_FORMAT.formatted(displayName(event.subscriberName()), event.playlistTitle())));
  }

  public List<NotificationRequestedEvent> from(PlaylistContentAddedEvent event) {
    return event.subscriberIds().stream()
        .map(receiverId -> request(sourceEventId(event.playlistId(), event.contentId()), receiverId, PLAYLIST_CONTENT_ADDED_TITLE,
            PLAYLIST_CONTENT_ADDED_CONTENT_FORMAT.formatted(event.playlistTitle(), event.contentTitle())))
        .toList();
  }

  public List<NotificationRequestedEvent> from(PlaylistCreatedEvent event) {
    return event.followerIds().stream()
        .map(receiverId -> request(event.playlistId(), receiverId, FOLLOWEE_ACTIVITY_TITLE,
            FOLLOWEE_ACTIVITY_CONTENT_FORMAT.formatted(displayName(event.ownerName()))))
        .toList();
  }

  public List<NotificationRequestedEvent> from(UserRoleChangedEvent event) {
    return List.of(request(sourceEventId(event.userId(), event.occurredAt().toEpochMilli()), event.userId(), USER_ROLE_CHANGED_TITLE,
        USER_ROLE_CHANGED_CONTENT_FORMAT.formatted(event.role().name())));
  }

  public List<NotificationRequestedEvent> from(DirectMessageSentEvent event) {
    return List.of(request(event.directMessageId(), event.receiverId(), DIRECT_MESSAGE_TITLE,
        DIRECT_MESSAGE_CONTENT_FORMAT.formatted(displayName(event.senderName()))));
  }

  public NotificationMessage from(NotificationRequestedEvent event) {
    return message(event.sourceEventId(), event);
  }

  public NotificationMessage fromKafkaRecord(Object value) {
    IntegrationEventEnvelope envelope = readEnvelope(value);
    if (envelope.eventId() == null || envelope.payload() == null) {
      throw new IllegalArgumentException("알림 Kafka 이벤트의 eventId와 payload는 필수입니다.");
    }
    if (!NotificationRequestedEvent.class.getSimpleName().equals(envelope.eventType())) {
      throw new IllegalArgumentException("지원하지 않는 알림 Kafka 이벤트입니다. eventType=" + envelope.eventType());
    }
    if (envelope.eventVersion() != NOTIFICATION_EVENT_VERSION) {
      throw new IllegalArgumentException("지원하지 않는 알림 Kafka 이벤트 버전입니다. eventVersion="
          + envelope.eventVersion());
    }
    try {
      NotificationRequestedEvent event = objectMapper.treeToValue(
          envelope.payload(), NotificationRequestedEvent.class);
      validate(event);
      return message(envelope.eventId(), event);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("알림 Kafka 이벤트 payload를 읽을 수 없습니다.", e);
    }
  }

  private void validate(NotificationRequestedEvent event) {
    if (event == null || event.sourceEventId() == null || event.receiverId() == null
        || !StringUtils.hasText(event.title()) || !StringUtils.hasText(event.content())
        || event.level() == null) {
      throw new IllegalArgumentException("알림 Kafka 이벤트 payload가 유효하지 않습니다.");
    }
  }

  private IntegrationEventEnvelope readEnvelope(Object value) {
    try {
      JsonNode node = toJsonNode(value);
      return objectMapper.treeToValue(node, IntegrationEventEnvelope.class);
    } catch (JsonProcessingException | IllegalArgumentException e) {
      throw new IllegalArgumentException("알림 Kafka 이벤트 envelope을 읽을 수 없습니다.", e);
    }
  }

  private JsonNode toJsonNode(Object value) throws JsonProcessingException {
    if (value instanceof String text) {
      return objectMapper.readTree(text);
    }
    if (value instanceof JsonNode jsonNode) {
      return jsonNode;
    }
    if (value instanceof JsonSchemaAndValue schemaAndValue) {
      return objectMapper.valueToTree(schemaAndValue.getValue());
    }
    return objectMapper.valueToTree(value);
  }

  private NotificationRequestedEvent request(UUID sourceEventId, UUID receiverId, String title, String content) {
    return new NotificationRequestedEvent(sourceEventId, receiverId, title, content, NotificationLevel.INFO);
  }

  private UUID sourceEventId(UUID first, Object second) {
    return UUID.nameUUIDFromBytes((first + ":" + second).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private NotificationMessage message(UUID eventId, NotificationRequestedEvent event) {
    if (eventId == null || event == null) {
      throw new IllegalArgumentException("알림 이벤트 식별자와 payload는 필수입니다.");
    }
    return new NotificationMessage(eventId, new NotificationCreateCommand(
        event.receiverId(), event.title(), event.content(), event.level()));
  }

  private String displayName(String name) {
    return StringUtils.hasText(name) ? name : DEFAULT_DISPLAY_NAME;
  }
}
