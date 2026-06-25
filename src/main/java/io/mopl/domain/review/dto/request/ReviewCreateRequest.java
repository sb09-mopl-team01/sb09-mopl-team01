package io.mopl.domain.review.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class ReviewCreateRequest {

  @NotNull(message = "콘텐츠 ID는 필수입니다.")
  private UUID contentId;

  @NotBlank(message = "리뷰 내용을 입력해주세요.")
  private String text;

  @DecimalMin(value = "0.0", message = "평점은 0.0 이상이어야 합니다.")
  @DecimalMax(value = "5.0", message = "평점은 5.0 이하이어야 합니다.")
  private double rating;
}
