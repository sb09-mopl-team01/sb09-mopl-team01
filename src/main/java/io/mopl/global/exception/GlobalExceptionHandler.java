package io.mopl.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(HttpMessageNotReadableException.class)
  protected ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException e, HttpServletRequest request) {

    log.warn("Malformed JSON request. uri={}, method={}, message={}",
        request.getRequestURI(), request.getMethod(), e.getMessage());

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(ErrorCode.INVALID_INPUT.getCode(), "요청 형식이 올바르지 않거나 JSON 파싱에 실패했습니다."));
  }

  @ExceptionHandler(Exception.class)
  protected ResponseEntity<ErrorResponse> handleAllUncaughtException(
      Exception e, HttpServletRequest request) {

    log.error("Unhandled exception occurred. uri={}, method={}",
        request.getRequestURI(), request.getMethod(), e);

    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorResponse(ErrorCode.INTERNAL_SERVER_ERROR.getCode(), "서버 내부에서 예상치 못한 오류가 발생했습니다."));
  }

  @ExceptionHandler(BaseException.class)
  protected ResponseEntity<ErrorResponse> handleBaseException(BaseException e, HttpServletRequest request) {
    ErrorCode errorCode = e.getErrorCode();
    log.warn("Business exception occurred. uri={}, method={}, code={}, message={}",
        request.getRequestURI(), request.getMethod(), errorCode.getCode(), e.getMessage());

    return ResponseEntity
        .status(errorCode.getStatus())
        .body(ErrorResponse.of(errorCode));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  protected ResponseEntity<ErrorResponse> handleValidationException(
      MethodArgumentNotValidException e, HttpServletRequest request) {
    String validationMessage = formatFieldErrors(e.getBindingResult());

    log.warn("Request body validation failed. uri={}, method={}, errors={}",
        request.getRequestURI(), request.getMethod(), validationMessage);

    String message = validationMessage.isBlank()
        ? ErrorCode.INVALID_INPUT.getMessage()
        : validationMessage;

    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorResponse(ErrorCode.INVALID_INPUT.getCode(), message));
  }


  private String formatFieldErrors(BindingResult bindingResult) {
    return bindingResult.getFieldErrors()
        .stream()
        .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
        .collect(Collectors.joining(", "));
  }

  @ExceptionHandler({DateTimeParseException.class, NumberFormatException.class})
  public ResponseEntity<ErrorResponse> handleParsingException(Exception e) {
    ErrorCode errorCode = ErrorCode.INVALID_INPUT;
    return ResponseEntity
        .status(errorCode.getStatus())
        .body(new ErrorResponse(errorCode.getCode(), "유효하지 않은 커서 형식입니다."));
  }
}
