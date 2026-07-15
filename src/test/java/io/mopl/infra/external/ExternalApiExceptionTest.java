package io.mopl.infra.external;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

class ExternalApiExceptionTest {

  @Test
  void plainExternalApiExceptionIsPermanentByDefault() {
    ExternalApiException exception = new ExternalApiException("missing API configuration");

    assertThat(exception.isRetryable()).isFalse();
    assertThat(exception.getStatusCode()).isNull();
  }

  @Test
  void fromRestClientFailure_marksRateLimitAndServerErrorAsRetryable() {
    ExternalApiException rateLimit = ExternalApiException.fromRestClientFailure(
        "rate limited",
        new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)
    );
    ExternalApiException serverError = ExternalApiException.fromRestClientFailure(
        "server failed",
        new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)
    );

    assertThat(rateLimit.isRetryable()).isTrue();
    assertThat(rateLimit.getStatusCode()).isEqualTo(429);
    assertThat(serverError.isRetryable()).isTrue();
    assertThat(serverError.getStatusCode()).isEqualTo(503);
  }

  @Test
  void fromRestClientFailure_marksOrdinaryClientErrorAsPermanent() {
    ExternalApiException exception = ExternalApiException.fromRestClientFailure(
        "bad request",
        new HttpClientErrorException(HttpStatus.BAD_REQUEST)
    );

    assertThat(exception.isRetryable()).isFalse();
    assertThat(exception.getStatusCode()).isEqualTo(400);
  }
}
