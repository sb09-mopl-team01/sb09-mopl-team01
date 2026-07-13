package io.mopl.domain.watchingsession.realtime;

import java.util.UUID;

public interface WatchingSessionPresenceStore {

  void enter(UUID watcherId, UUID contentId);

  void leave(UUID watcherId, UUID contentId);
}
