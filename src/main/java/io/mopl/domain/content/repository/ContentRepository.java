package io.mopl.domain.content.repository;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentRepository extends JpaRepository<Content, UUID>, ContentRepositoryCustom {

  boolean existsBySourceAndTypeAndExternalId(
      ContentSource source,
      ContentType type,
      String externalId
  );

  Optional<Content> findBySourceAndTypeAndExternalId(
      ContentSource source,
      ContentType type,
      String externalId
  );

  List<Content> findAllBySourceInAndTypeInAndExternalIdIn(
      Collection<ContentSource> sources,
      Collection<ContentType> types,
      Collection<String> externalIds
  );

  @Query("select distinct c from Content c left join fetch c.tags where c.id in :contentIds")
  List<Content> findAllByIdWithTags(@Param("contentIds") Collection<UUID> contentIds);
}
