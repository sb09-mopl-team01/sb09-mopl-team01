package io.mopl.infra.external.tmdb;

import io.mopl.infra.external.ExternalApiException;
import io.mopl.infra.external.ExternalContentApiProperties;
import io.mopl.infra.external.ExternalContentCandidate;
import io.mopl.infra.external.ExternalContentClient;
import io.mopl.infra.external.ExternalContentFetchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class TmdbExternalContentClient implements ExternalContentClient {

  private static final ParameterizedTypeReference<TmdbContentResponse<TmdbMovieItem>> MOVIE_RESPONSE_TYPE =
      new ParameterizedTypeReference<>() {
      };
  private static final ParameterizedTypeReference<TmdbContentResponse<TmdbTvItem>> TV_RESPONSE_TYPE =
      new ParameterizedTypeReference<>() {
      };

  private final RestClient restClient;
  private final ExternalContentApiProperties.Tmdb properties;
  private final TmdbContentMapper tmdbContentMapper;

  public TmdbExternalContentClient(
      RestClient.Builder restClientBuilder,
      ExternalContentApiProperties properties,
      TmdbContentMapper tmdbContentMapper
  ) {
    this.properties = properties.tmdb();
    this.restClient = restClientBuilder.baseUrl(this.properties.baseUrl()).build();
    this.tmdbContentMapper = tmdbContentMapper;
  }

  @Override
  public ExternalContentFetchResult fetchContents() {
    if (isBlank(properties.apiKey())) {
      throw new ExternalApiException("TMDB API key is required.");
    }

    ExternalContentFetchResult result = ExternalContentFetchResult.empty();
    for (int page = 1; page <= Math.max(1, properties.pages()); page++) {
      result = result.merge(fetchMovies(page));
      result = result.merge(fetchTvSeries(page));
    }
    return result;
  }

  private ExternalContentFetchResult fetchMovies(int page) {
    List<TmdbMovieItem> items;
    try {
      TmdbContentResponse<TmdbMovieItem> response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path(properties.moviePath())
              .queryParam("api_key", properties.apiKey())
              .queryParam("page", page)
              .queryParam("language", language())
              .queryParam("region", region())
              .build())
          .retrieve()
          .body(MOVIE_RESPONSE_TYPE);
      items = response == null || response.results() == null ? List.of() : response.results();
    } catch (Exception e) {
      throw new ExternalApiException("TMDB movie API request failed.", e);
    }
    return mapCandidates(
        items,
        item -> tmdbContentMapper.toMovieCandidate(item, properties.imageBaseUrl()),
        "movie"
    );
  }

  private ExternalContentFetchResult fetchTvSeries(int page) {
    List<TmdbTvItem> items;
    try {
      TmdbContentResponse<TmdbTvItem> response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path(properties.tvPath())
              .queryParam("api_key", properties.apiKey())
              .queryParam("page", page)
              .queryParam("language", language())
              .build())
          .retrieve()
          .body(TV_RESPONSE_TYPE);
      items = response == null || response.results() == null ? List.of() : response.results();
    } catch (Exception e) {
      throw new ExternalApiException("TMDB tv API request failed.", e);
    }
    return mapCandidates(
        items,
        item -> tmdbContentMapper.toTvCandidate(item, properties.imageBaseUrl()),
        "tvSeries"
    );
  }

  private <T> ExternalContentFetchResult mapCandidates(
      List<T> items,
      Function<T, ExternalContentCandidate> mapper,
      String contentType
  ) {
    List<ExternalContentCandidate> candidates = new ArrayList<>();
    int filteredCount = 0;
    int failedCount = 0;
    for (T item : items) {
      try {
        ExternalContentCandidate candidate = mapper.apply(item);
        if (candidate == null) {
          failedCount++;
        } else if (shouldKeepCandidate(candidate)) {
          candidates.add(candidate);
        } else {
          filteredCount++;
        }
      } catch (RuntimeException e) {
        failedCount++;
        log.warn(
            "Content external item mapping failed. client=TMDB, contentType={}, errorType={}, message={}",
            contentType,
            e.getClass().getSimpleName(),
            e.getMessage()
        );
      }
    }
    return new ExternalContentFetchResult(items.size(), candidates, filteredCount, failedCount);
  }

  private boolean shouldKeepCandidate(ExternalContentCandidate candidate) {
    if (candidate == null) {
      return false;
    }
    if (!properties.koreanOnly()) {
      return true;
    }
    return containsHangul(candidate.title()) || containsHangul(candidate.description());
  }

  private String language() {
    String language = properties.language();
    return isBlank(language) ? "ko-KR" : language.trim();
  }

  private String region() {
    String region = properties.region();
    return isBlank(region) ? "KR" : region.trim();
  }

  private static boolean containsHangul(String value) {
    return !isBlank(value) && value.codePoints()
        .anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HANGUL);
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
