package io.mopl.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

  // 공통
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류"),
  INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 요청입니다"),
  DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "COMMON_409", "이미 존재하는 데이터입니다"),
  TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "COMMON_429", "요청이 너무 빠릅니다. 잠시 후 다시 시도해주세요."),


  ALREADY_REVIEWED(HttpStatus.CONFLICT, "REVIEW_409", "이미 작성한 리뷰가 존재합니다.")
  ;

  private final HttpStatus status;
  private final String code;
  private final String message;
}