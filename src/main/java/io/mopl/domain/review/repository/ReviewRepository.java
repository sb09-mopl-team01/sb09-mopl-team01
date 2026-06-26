package io.mopl.domain.review.repository;

import io.mopl.domain.review.entity.Review;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID>, ReviewRepositoryCustom {

  Page<Review> findByContentId(UUID contentId, Pageable pageable);

  boolean existsByAuthorIdAndContentId(UUID authorId, UUID contentId);

  @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.content.id = :contentId")
  double calculateAverageRatingByContentId(@Param("contentId") UUID contentId);

}
