package io.mopl.domain.review.controller;

import io.mopl.domain.review.dto.request.ReviewCreateRequest;
import io.mopl.domain.review.dto.ReviewDto;
import io.mopl.domain.review.dto.request.ReviewUpdateRequest;
import io.mopl.domain.review.service.ReviewService;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.security.MoplUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {

  private final ReviewService reviewService;

  @PostMapping
  public ResponseEntity<ReviewDto> createReview(

      @AuthenticationPrincipal MoplUserDetails userDetails,
      @Valid @RequestBody ReviewCreateRequest request) {

    UUID userId = userDetails.getUser().getId();

    log.info("컨트롤러 확인 - userId: {}", userId); // 여기서 로그 확인

    ReviewDto response = reviewService.createReview(userId, request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PatchMapping("/{reviewId}")
  public ResponseEntity<ReviewDto> updateReview(

      @AuthenticationPrincipal MoplUserDetails userDetails, // UUID 대신 객체 사용
      @PathVariable UUID reviewId,
      @Valid @RequestBody ReviewUpdateRequest request) {

    UUID userId = userDetails.getUser().getId(); // 여기서 ID 추출
    ReviewDto response = reviewService.updateReview(userId, reviewId, request);
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<CursorResponse<ReviewDto>> findReviews(
      @RequestParam(required = false) UUID contentId,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) UUID idAfter,
      @RequestParam(defaultValue = "10") int limit,
      @RequestParam String sortDirection,
      @RequestParam String sortBy) {

    validatePageParams(limit, sortDirection, sortBy);

    CursorResponse<ReviewDto> response = reviewService.findReviews(
        contentId, cursor, idAfter, limit, sortDirection, sortBy
    );
    return ResponseEntity.ok(response);
  }

  private void validatePageParams(int limit, String sortDirection, String sortBy) {
    if (limit <= 0) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
    if (!List.of("createdAt", "rating").contains(sortBy)) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
    if (!List.of("ASCENDING", "DESCENDING").contains(sortDirection.toUpperCase())) {
      throw new BaseException(ErrorCode.INVALID_INPUT);
    }
  }
  @DeleteMapping("/{reviewId}")
  public ResponseEntity<Void> deleteReview(

      @AuthenticationPrincipal MoplUserDetails userDetails, // UUID 대신 객체 사용
      @PathVariable UUID reviewId) {

    UUID userId = userDetails.getUser().getId(); // 여기서 ID 추출
    reviewService.deleteReview(userId, reviewId);
    return ResponseEntity.ok().build();
  }


}