package io.mopl.domain.follow.repository;

import java.util.List;
import java.util.UUID;

public interface FollowRepositoryCustom {

  List<UUID> findFollowerIdsByFolloweeId(UUID followeeId);
}
