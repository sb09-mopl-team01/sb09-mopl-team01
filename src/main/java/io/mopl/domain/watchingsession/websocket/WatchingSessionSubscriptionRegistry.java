package io.mopl.domain.watchingsession.websocket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WatchingSessionSubscriptionRegistry {

  private final Map<SubscriptionKey, WatchingSessionSubscription> subscriptions = new HashMap<>();
  private final Map<WatchingSessionSubscription, Integer> activeCounts = new HashMap<>();

  public synchronized void register(
      String sessionId,
      String subscriptionId,
      UUID watcherId,
      UUID contentId
  ) {
    if (sessionId == null || subscriptionId == null) {
      return;
    }

    SubscriptionKey key = new SubscriptionKey(sessionId, subscriptionId);
    WatchingSessionSubscription subscription =
        new WatchingSessionSubscription(watcherId, contentId);
    WatchingSessionSubscription previous = subscriptions.putIfAbsent(key, subscription);
    if (previous == null) {
      activeCounts.merge(subscription, 1, Integer::sum);
    }
  }

  public synchronized List<WatchingSessionSubscription> unregister(
      String sessionId,
      String subscriptionId
  ) {
    if (sessionId == null || subscriptionId == null) {
      return List.of();
    }

    WatchingSessionSubscription subscription =
        subscriptions.remove(new SubscriptionKey(sessionId, subscriptionId));
    return decrement(subscription);
  }

  public synchronized List<WatchingSessionSubscription> unregisterSession(String sessionId) {
    if (sessionId == null) {
      return List.of();
    }

    List<WatchingSessionSubscription> endedSubscriptions = new ArrayList<>();
    List<SubscriptionKey> keys = subscriptions.keySet().stream()
        .filter(key -> key.sessionId().equals(sessionId))
        .toList();
    for (SubscriptionKey key : keys) {
      WatchingSessionSubscription subscription = subscriptions.remove(key);
      endedSubscriptions.addAll(decrement(subscription));
    }
    return endedSubscriptions;
  }

  private List<WatchingSessionSubscription> decrement(WatchingSessionSubscription subscription) {
    if (subscription == null) {
      return List.of();
    }

    int nextCount = activeCounts.getOrDefault(subscription, 0) - 1;
    if (nextCount > 0) {
      activeCounts.put(subscription, nextCount);
      return List.of();
    }

    activeCounts.remove(subscription);
    return List.of(subscription);
  }

  private record SubscriptionKey(String sessionId, String subscriptionId) {

    private SubscriptionKey {
      Objects.requireNonNull(sessionId, "sessionId는 필수입니다.");
      Objects.requireNonNull(subscriptionId, "subscriptionId는 필수입니다.");
    }
  }
}
