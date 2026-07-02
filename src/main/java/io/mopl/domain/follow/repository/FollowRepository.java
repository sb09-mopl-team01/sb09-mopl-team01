package io.mopl.domain.follow.repository;

import io.mopl.domain.follow.entity.Follow;
import io.mopl.domain.user.entity.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

  boolean existsByFollowerAndFollowee(User follower, User followee);

  Optional<Follow> findByFollowerAndFollowee(User follower, User followee);
}
