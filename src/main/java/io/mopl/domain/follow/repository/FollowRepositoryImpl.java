package io.mopl.domain.follow.repository;

import static io.mopl.domain.follow.entity.QFollow.follow;

import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FollowRepositoryImpl implements FollowRepositoryCustom {

  private final JPAQueryFactory queryFactory;

  @Override
  public List<UUID> findFollowerIdsByFolloweeId(UUID followeeId) {
    return queryFactory
        .select(follow.follower.id)
        .from(follow)
        .where(follow.followee.id.eq(followeeId))
        .fetch();
  }
}
