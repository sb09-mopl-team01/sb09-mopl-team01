package io.mopl.infra.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mopl.external")
public record ExternalContentApiProperties(
    Timeout timeout,
    Retry retry,
    Tmdb tmdb,
    TheSportsDb theSportsDb
) {

  public ExternalContentApiProperties {
    if (timeout == null) {
      timeout = new Timeout(3, 5);
    }
    if (retry == null) {
      retry = new Retry(3, 1000, 2.0, 4000);
    }
    if (tmdb == null) {
      tmdb = new Tmdb(null, "https://api.themoviedb.org/3", "https://image.tmdb.org/t/p/w500",
          "/movie/popular", "/tv/popular", "ko-KR", "KR", true, 1);
    }
    if (theSportsDb == null) {
      theSportsDb = new TheSportsDb(null, "https://www.thesportsdb.com/api/v1/json", null,
          "/{apiKey}/eventsnextleague.php");
    }
  }

  public record Timeout(
      int connectSeconds,
      int readSeconds
  ) {

    public Timeout {
      if (connectSeconds < 1 || readSeconds < 1) {
        throw new IllegalArgumentException("외부 API timeout은 1초 이상이어야 합니다.");
      }
    }
  }

  public record Retry(
      int maxAttempts,
      long initialBackoffMillis,
      double multiplier,
      long maxBackoffMillis
  ) {

    public Retry {
      if (maxAttempts < 1) {
        throw new IllegalArgumentException("외부 API 최대 시도 횟수는 1회 이상이어야 합니다.");
      }
      if (initialBackoffMillis < 0 || maxBackoffMillis < 0) {
        throw new IllegalArgumentException("외부 API backoff 시간은 음수일 수 없습니다.");
      }
      if (!Double.isFinite(multiplier) || multiplier < 1.0) {
        throw new IllegalArgumentException("외부 API backoff 배수는 1 이상이어야 합니다.");
      }
      if (maxBackoffMillis < initialBackoffMillis) {
        throw new IllegalArgumentException("외부 API 최대 backoff는 최초 backoff보다 작을 수 없습니다.");
      }
    }
  }

  public record Tmdb(
      String apiKey,
      String baseUrl,
      String imageBaseUrl,
      String moviePath,
      String tvPath,
      String language,
      String region,
      boolean koreanOnly,
      int pages
  ) {

    public Tmdb {
      requireText(baseUrl, "TMDB base URL");
      requireText(imageBaseUrl, "TMDB image base URL");
      requireText(moviePath, "TMDB movie path");
      requireText(tvPath, "TMDB TV path");
      if (pages < 1) {
        throw new IllegalArgumentException("TMDB 수집 page 수는 1 이상이어야 합니다.");
      }
    }
  }

  public record TheSportsDb(
      String apiKey,
      String baseUrl,
      String leagueId,
      String eventsPath
  ) {

    public TheSportsDb {
      requireText(baseUrl, "TheSportsDB base URL");
      requireText(eventsPath, "TheSportsDB events path");
    }
  }

  private static void requireText(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + "은(는) 필수입니다.");
    }
  }
}

