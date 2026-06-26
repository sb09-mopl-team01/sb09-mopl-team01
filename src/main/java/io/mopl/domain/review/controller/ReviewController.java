package io.mopl.domain.review.controller;

import io.mopl.domain.review.dto.request.ReviewCreateRequest;
import io.mopl.domain.review.dto.ReviewDto;
import io.mopl.domain.review.dto.request.ReviewUpdateRequest;
import io.mopl.domain.review.service.ReviewService;
import io.mopl.global.response.CursorResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

  private final ReviewService reviewService;

  @PostMapping
  public ResponseEntity<ReviewDto> createReview(

      // 임시
      // Security 연동 완료 시 @AuthenticationPrincipal 로 대체
      @RequestAttribute("userId") UUID userId,
      @Valid @RequestBody ReviewCreateRequest request) {

    ReviewDto response = reviewService.createReview(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PatchMapping("/{reviewId}")
  public ResponseEntity<ReviewDto> updateReview(

      // 임시
      // Security 연동 완료 시 @AuthenticationPrincipal 로 대체
      @RequestAttribute("userId") UUID userId,
      @PathVariable UUID reviewId,
      @Valid @RequestBody ReviewUpdateRequest request) {

    ReviewDto response = reviewService.updateReview(userId, reviewId, request);
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<CursorResponse<ReviewDto>> findReviews(
      @RequestParam(required = false) UUID contentId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam int limit,
      @RequestParam String sortDirection,
      @RequestParam String sortBy) {

    CursorResponse<ReviewDto> response = reviewService.findReviews(
        contentId, cursor, idAfter, limit, sortDirection, sortBy
    );
    return ResponseEntity.ok(response);
  }

  @DeleteMapping("/{reviewId}")
  public ResponseEntity<Void> deleteReview(
      @RequestAttribute("userId") UUID userId,
      @PathVariable UUID reviewId) {

    reviewService.deleteReview(userId, reviewId);
    return ResponseEntity.ok().build();
  }
}