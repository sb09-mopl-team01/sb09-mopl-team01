package io.mopl.domain.review.repository;

import io.mopl.domain.review.entity.Review;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID>, ReviewRepositoryCustom {

  @Query("SELECT r FROM Review r WHERE r.content.id = :contentId AND r.content.deletedAt IS NULL")
  Page<Review> findByContentId(@Param("contentId") UUID contentId, Pageable pageable);

  @Query("SELECT r FROM Review r WHERE r.id = :reviewId AND r.content.deletedAt IS NULL")
  Optional<Review> findActiveById(@Param("reviewId") UUID reviewId);

  boolean existsByAuthorIdAndContentId(UUID authorId, UUID contentId);

  @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.content.id = :contentId")
  double calculateAverageRatingByContentId(@Param("contentId") UUID contentId);

  long countByContentId(UUID contentId);

  @Query("SELECT COUNT(r) FROM Review r WHERE r.content.id = :contentId AND r.content.deletedAt IS NULL")
  long countVisibleByContentId(@Param("contentId") UUID contentId);
}
