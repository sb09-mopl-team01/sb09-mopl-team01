package io.mopl.domain.watchingsession.realtime;

import io.mopl.domain.watchingsession.dto.WatchingSessionChange;

public interface WatchingSessionRealtimePublisher {

  void publish(WatchingSessionChange change);
}
