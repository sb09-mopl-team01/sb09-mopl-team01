package io.mopl.domain.watchingsession.realtime;

import io.mopl.domain.watchingsession.websocket.WatchingSessionSubscription;
import java.util.List;

/**
 * 여러 애플리케이션 인스턴스에 걸친 시청 구독 lease를 관리합니다.
 */
public interface WatchingSessionLeaseStore {

  boolean acquire(WatchingSessionSubscription subscription, String nodeId);

  boolean release(WatchingSessionSubscription subscription, String nodeId);

  boolean refresh(WatchingSessionSubscription subscription, String nodeId);

  List<WatchingSessionSubscription> expireStaleLeases();

  boolean claimRecovery(WatchingSessionSubscription subscription, String recoveryOwnerId);

  boolean completeRecovery(WatchingSessionSubscription subscription, String recoveryOwnerId);

  WatchingSessionLeaseRecoveryFailure recordRecoveryFailure(
      WatchingSessionSubscription subscription,
      String recoveryOwnerId
  );
}
