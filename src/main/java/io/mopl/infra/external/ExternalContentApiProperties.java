package io.mopl.infra.external;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mopl.external")
public record ExternalContentApiProperties(
    Timeout timeout,
    Tmdb tmdb,
    TheSportsDb theSportsDb
) {

  public ExternalContentApiProperties {
    if (timeout == null) {
      timeout = new Timeout(3, 5);
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
  }

  public record TheSportsDb(
      String apiKey,
      String baseUrl,
      String leagueId,
      String eventsPath
  ) {
  }
}

