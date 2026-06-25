package io.mopl.domain.review.dto;

import io.mopl.domain.review.entity.Review;
import io.mopl.domain.review.replica.User.UserSummary;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReviewDto {
  private UUID id;
  private UUID contentId;
  private UserSummary author;
  private String text;
  private double rating;

}