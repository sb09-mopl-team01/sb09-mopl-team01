package io.mopl.domain.playlist.repository;

import io.mopl.domain.playlist.entity.Playlist;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID>, PlaylistRepositoryCustom {

  @Modifying(clearAutomatically = true)
  @Query("UPDATE Playlist p SET p.subscriberCount = p.subscriberCount + 1 WHERE p.id = :id")
  void increaseSubscriberCount(@Param("id") UUID id);

  @Modifying(clearAutomatically = true)
  @Query("UPDATE Playlist p SET p.subscriberCount = p.subscriberCount - 1 WHERE p.id = :id AND p.subscriberCount > 0")
  void decreaseSubscriberCount(@Param("id") UUID id);

  java.util.Optional<io.mopl.domain.playlist.entity.Playlist> findFirstByOwnerIdOrderByCreatedAtDesc(java.util.UUID ownerId);
}
