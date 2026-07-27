package io.mopl.domain.directmessage.realtime;

import java.util.UUID;

public final class DirectMessageTopic {

  private static final String DIRECT_MESSAGE_TOPIC = "/sub/conversations/%s/direct-messages";

  private DirectMessageTopic() {
  }

  public static String of(UUID conversationId) {
    return DIRECT_MESSAGE_TOPIC.formatted(conversationId);
  }
}
