package io.mopl.domain.review.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReviewUpdateRequest {

  @NotBlank(message = "수정할 리뷰 내용을 입력해주세요.")
  private String text;

  @DecimalMin(value = "0.0", message = "평점은 0.0 이상이어야 합니다.")
  @DecimalMax(value = "5.0", message = "평점은 5.0 이하이어야 합니다.")
  private double rating;
}
