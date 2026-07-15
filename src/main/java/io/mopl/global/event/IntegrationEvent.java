package io.mopl.global.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;

/**
 * 도메인 변경을 다른 서비스에 전달하기 위한 영속 이벤트 입력 모델입니다.
 */
public record IntegrationEvent(
    String topic,
    String key,
    String eventType,
    int eventVersion,
    String aggregateType,
    UUID aggregateId,
    JsonNode payload
) {

  private static final int TOPIC_MAX_LENGTH = 249;
  private static final int KEY_MAX_LENGTH = 255;
  private static final int EVENT_TYPE_MAX_LENGTH = 150;
  private static final int AGGREGATE_TYPE_MAX_LENGTH = 100;

  public IntegrationEvent {
    requireText(topic, "topic", TOPIC_MAX_LENGTH);
    requireText(key, "key", KEY_MAX_LENGTH);
    requireText(eventType, "eventType", EVENT_TYPE_MAX_LENGTH);
    requireText(aggregateType, "aggregateType", AGGREGATE_TYPE_MAX_LENGTH);
    if (eventVersion < 1) {
      throw new IllegalArgumentException("eventVersion은 1 이상이어야 합니다.");
    }
    Objects.requireNonNull(aggregateId, "aggregateId must not be null");
    if (payload == null || !payload.isObject()) {
      throw new IllegalArgumentException("payload는 JSON 객체여야 합니다.");
    }
  }

  private static void requireText(String value, String fieldName, int maxLength) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + "은(는) 비어 있을 수 없습니다.");
    }
    if (value.length() > maxLength) {
      throw new IllegalArgumentException(fieldName + "은(는) " + maxLength + "자를 초과할 수 없습니다.");
    }
  }
}
