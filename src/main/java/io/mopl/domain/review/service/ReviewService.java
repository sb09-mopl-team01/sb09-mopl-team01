package io.mopl.domain.review.service;


import io.mopl.domain.review.dto.ReviewDto;
import io.mopl.domain.review.dto.request.ReviewCreateRequest;
import io.mopl.domain.review.dto.request.ReviewUpdateRequest;
import io.mopl.domain.review.entity.Review;
import io.mopl.domain.review.mapper.ReviewMapper;
import io.mopl.domain.review.replica.Content.Content;
import io.mopl.domain.review.replica.Content.ContentRepository;
import io.mopl.domain.review.replica.User.User;
import io.mopl.domain.review.replica.User.UserRepository;
import io.mopl.domain.review.repository.ReviewRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final UserRepository userRepository;
  private final ContentRepository contentRepository;
  private final ReviewMapper reviewMapper;

  @Transactional
  public ReviewDto createReview(UUID userId, ReviewCreateRequest request) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT));
    Content content = contentRepository.findById(request.getContentId())
        .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT));

    if (reviewRepository.existsByAuthorIdAndContentId(userId, request.getContentId())) {
      throw new BaseException(ErrorCode.DUPLICATE_RESOURCE);
    }

    Review review = Review.builder()
        .author(user)
        .content(content)
        .text(request.getText())
        .rating(request.getRating())
        .build();

    Review savedReview = reviewRepository.save(review);

    syncContentAverageRating(content);

    return reviewMapper.toDto(savedReview);
  }

  @Transactional
  public ReviewDto updateReview(UUID userId, UUID reviewId, ReviewUpdateRequest request) {
    Review review = reviewRepository.findById(reviewId)
        .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT));

    if (!review.getAuthor().getId().equals(userId)) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
    review.update(request.getText(), request.getRating());
    syncContentAverageRating(review.getContent());

    return reviewMapper.toDto(review);
  }

  public CursorResponse<ReviewDto> findReviews(
      UUID contentId, String cursor, UUID idAfter, int limit,
      String sortDirection, String sortBy) {


    List<Review> reviews = reviewRepository.findReviewsByCursor(
        contentId, cursor, idAfter, limit, sortDirection, sortBy
    );

    List<ReviewDto> reviewDtos = reviews.stream()
        .map(reviewMapper::toDto)
        .toList();

    boolean hasNext = reviews.size() == limit;
    String nextCursor = hasNext ? calculateNextCursor(reviews.get(reviews.size() - 1), sortBy) : null;
    UUID nextIdAfter = hasNext ? reviews.get(reviews.size() - 1).getId() : null;
    long totalCount = reviewRepository.count();

    return new CursorResponse<>(
        reviewDtos,
        nextCursor,
        nextIdAfter,
        hasNext,
        totalCount,
        sortBy,
        SortDirection.valueOf(sortDirection.toUpperCase())
    );
  }

  private String calculateNextCursor(Review lastReview, String sortBy) {
    if ("rating".equals(sortBy)) {
      return String.valueOf(lastReview.getRating());
    }
    return lastReview.getCreatedAt().toString();
  }

  @Transactional
  public void deleteReview(UUID userId, UUID reviewId) {
    Review review = reviewRepository.findById(reviewId)
        .orElseThrow(() -> new BaseException(ErrorCode.INVALID_INPUT));

    if (!review.getAuthor().getId().equals(userId)) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }

    Content content = review.getContent();
    reviewRepository.delete(review);

    syncContentAverageRating(content);
  }

  private void syncContentAverageRating(Content content) {
    double averageRating = reviewRepository.calculateAverageRatingByContentId(content.getId());
    content.updateAverageRating(averageRating);
  }
}