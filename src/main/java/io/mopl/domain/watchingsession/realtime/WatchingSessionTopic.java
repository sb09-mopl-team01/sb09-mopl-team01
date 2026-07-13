package io.mopl.domain.watchingsession.realtime;

import java.util.UUID;

final class WatchingSessionTopic {

  private static final String WATCHING_SESSION_TOPIC = "/sub/contents/%s/watch";

  private WatchingSessionTopic() {
  }

  static String of(UUID contentId) {
    return WATCHING_SESSION_TOPIC.formatted(contentId);
  }
}
