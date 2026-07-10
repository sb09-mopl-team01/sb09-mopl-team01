package io.mopl.infra.external;

import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ExternalApiRetryExecutor {

  private final ExternalContentApiProperties.Retry retry;

  public ExternalApiRetryExecutor(ExternalContentApiProperties properties) {
    this.retry = properties.retry();
  }

  public <T> T execute(String operation, Supplier<T> action) {
    int attempt = 1;
    while (true) {
      try {
        return action.get();
      } catch (ExternalApiException e) {
        if (!e.isRetryable() || attempt >= retry.maxAttempts()) {
          throw e;
        }

        long backoffMillis = calculateBackoffMillis(attempt);
        log.warn(
            "Content external API retry scheduled. operation={}, attempt={}, maxAttempts={}, backoffMillis={}, statusCode={}",
            operation,
            attempt,
            retry.maxAttempts(),
            backoffMillis,
            e.getStatusCode()
        );
        sleep(backoffMillis);
        attempt++;
      }
    }
  }

  protected void sleep(long backoffMillis) {
    try {
      Thread.sleep(backoffMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ExternalApiException("외부 API 재시도 대기가 중단되었습니다.", e);
    }
  }

  private long calculateBackoffMillis(int failedAttempt) {
    double calculated = retry.initialBackoffMillis()
        * Math.pow(retry.multiplier(), Math.max(0, failedAttempt - 1));
    return Math.min(retry.maxBackoffMillis(), Math.round(calculated));
  }
}
