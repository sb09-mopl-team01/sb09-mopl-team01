package io.mopl.domain.playlist.repository;

import static io.mopl.domain.playlist.entity.QPlaylistSubscription.playlistSubscription;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PlaylistSubscriptionRepositoryImpl implements PlaylistSubscriptionRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<UUID> findSubscriberIdsByPlaylistId(UUID playlistId) {
    return queryFactory
        .select(playlistSubscription.user.id)
        .from(playlistSubscription)
        .where(playlistSubscription.playlist.id.eq(playlistId))
        .fetch();
  }
}
