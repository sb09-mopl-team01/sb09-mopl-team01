package io.mopl.infra.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class ExternalApiRetryExecutorTest {

  @Test
  void execute_retriesTransientFailureWithExponentialBackoff() {
    NoSleepRetryExecutor executor = executor(new ExternalContentApiProperties.Retry(3, 100, 2.0, 500));
    AtomicInteger attempts = new AtomicInteger();

    String result = executor.execute("test operation", () -> {
      if (attempts.incrementAndGet() < 3) {
        throw ExternalApiException.fromRestClientFailure(
            "temporary failure",
            new ResourceAccessException("timeout")
        );
      }
      return "completed";
    });

    assertThat(result).isEqualTo("completed");
    assertThat(attempts).hasValue(3);
    assertThat(executor.backoffHistory()).containsExactly(100L, 200L);
  }

  @Test
  void execute_doesNotRetryPermanentFailure() {
    NoSleepRetryExecutor executor = executor(new ExternalContentApiProperties.Retry(3, 100, 2.0, 500));
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(() -> executor.execute("test operation", () -> {
      attempts.incrementAndGet();
      throw ExternalApiException.fromRestClientFailure(
          "authentication failed",
          new HttpClientErrorException(HttpStatus.UNAUTHORIZED)
      );
    }))
        .isInstanceOf(ExternalApiException.class)
        .satisfies(exception -> {
          ExternalApiException externalApiException = (ExternalApiException) exception;
          assertThat(externalApiException.isRetryable()).isFalse();
          assertThat(externalApiException.getStatusCode()).isEqualTo(401);
        });

    assertThat(attempts).hasValue(1);
    assertThat(executor.backoffHistory()).isEmpty();
  }

  @Test
  void execute_stopsAfterConfiguredMaximumAttempts() {
    NoSleepRetryExecutor executor = executor(new ExternalContentApiProperties.Retry(3, 100, 3.0, 250));
    AtomicInteger attempts = new AtomicInteger();

    assertThatThrownBy(() -> executor.execute("test operation", () -> {
      attempts.incrementAndGet();
      throw ExternalApiException.fromRestClientFailure(
          "temporary failure",
          new ResourceAccessException("timeout")
      );
    }))
        .isInstanceOf(ExternalApiException.class)
        .satisfies(exception -> assertThat(((ExternalApiException) exception).isRetryable()).isTrue());

    assertThat(attempts).hasValue(3);
    assertThat(executor.backoffHistory()).containsExactly(100L, 250L);
  }

  private NoSleepRetryExecutor executor(ExternalContentApiProperties.Retry retry) {
    return new NoSleepRetryExecutor(properties(retry));
  }

  private ExternalContentApiProperties properties(ExternalContentApiProperties.Retry retry) {
    return new ExternalContentApiProperties(
        new ExternalContentApiProperties.Timeout(3, 5),
        retry,
        new ExternalContentApiProperties.Tmdb(
            null,
            "https://api.themoviedb.org/3",
            "https://image.tmdb.org/t/p/w500",
            "/movie/popular",
            "/tv/popular",
            "ko-KR",
            "KR",
            true,
            1
        ),
        new ExternalContentApiProperties.TheSportsDb(
            null,
            "https://www.thesportsdb.com/api/v1/json",
            null,
            "/{apiKey}/eventsnextleague.php"
        )
    );
  }

  private static class NoSleepRetryExecutor extends ExternalApiRetryExecutor {

    private final List<Long> backoffHistory = new ArrayList<>();

    private NoSleepRetryExecutor(ExternalContentApiProperties properties) {
      super(properties);
    }

    @Override
    protected void sleep(long backoffMillis) {
      backoffHistory.add(backoffMillis);
    }

    private List<Long> backoffHistory() {
      return backoffHistory;
    }
  }
}
