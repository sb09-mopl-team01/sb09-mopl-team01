package io.mopl.domain.review.repository;

import io.mopl.domain.review.entity.Review;
import java.util.List;
import java.util.UUID;

public interface ReviewRepositoryCustom {
  List<Review> findReviewsByCursor(
      UUID contentId,
      String cursor,
      UUID idAfter,
      int limit,
      String sortDirection,
      String sortBy
  );
}