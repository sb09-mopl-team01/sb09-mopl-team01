package io.mopl.infra.external;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class ExternalApiException extends RuntimeException {

  private final boolean retryable;
  private final Integer statusCode;

  public ExternalApiException(String message) {
    this(message, null, false, null);
  }

  public ExternalApiException(String message, Throwable cause) {
    this(message, cause, false, null);
  }

  private ExternalApiException(
      String message,
      Throwable cause,
      boolean retryable,
      Integer statusCode
  ) {
    super(message, cause);
    this.retryable = retryable;
    this.statusCode = statusCode;
  }

  public static ExternalApiException fromRestClientFailure(
      String message,
      RestClientException cause
  ) {
    if (cause instanceof RestClientResponseException responseException) {
      int statusCode = responseException.getStatusCode().value();
      boolean retryable = statusCode == 429 || responseException.getStatusCode().is5xxServerError();
      return new ExternalApiException(message, cause, retryable, statusCode);
    }
    if (cause instanceof ResourceAccessException) {
      return new ExternalApiException(message, cause, true, null);
    }
    return new ExternalApiException(message, cause, false, null);
  }

  public boolean isRetryable() {
    return retryable;
  }

  public Integer getStatusCode() {
    return statusCode;
  }
}
