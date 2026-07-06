package io.mopl.infra.external.tmdb;

import io.mopl.infra.external.ExternalApiException;
import io.mopl.infra.external.ExternalContentApiProperties;
import io.mopl.infra.external.ExternalContentCandidate;
import io.mopl.infra.external.ExternalContentClient;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
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
  public List<ExternalContentCandidate> fetchContents() {
    if (isBlank(properties.apiKey())) {
      throw new ExternalApiException("TMDB API key is required.");
    }

    List<ExternalContentCandidate> candidates = new ArrayList<>();
    for (int page = 1; page <= Math.max(1, properties.pages()); page++) {
      candidates.addAll(fetchMovies(page));
      candidates.addAll(fetchTvSeries(page));
    }
    return candidates;
  }

  private List<ExternalContentCandidate> fetchMovies(int page) {
    try {
      TmdbContentResponse<TmdbMovieItem> response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path(properties.moviePath())
              .queryParam("api_key", properties.apiKey())
              .queryParam("page", page)
              .build())
          .retrieve()
          .body(MOVIE_RESPONSE_TYPE);

      return response == null || response.results() == null
          ? List.of()
          : response.results().stream()
              .map(item -> tmdbContentMapper.toMovieCandidate(item, properties.imageBaseUrl()))
              .toList();
    } catch (Exception e) {
      throw new ExternalApiException("TMDB movie API request failed.", e);
    }
  }

  private List<ExternalContentCandidate> fetchTvSeries(int page) {
    try {
      TmdbContentResponse<TmdbTvItem> response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path(properties.tvPath())
              .queryParam("api_key", properties.apiKey())
              .queryParam("page", page)
              .build())
          .retrieve()
          .body(TV_RESPONSE_TYPE);

      return response == null || response.results() == null
          ? List.of()
          : response.results().stream()
              .map(item -> tmdbContentMapper.toTvCandidate(item, properties.imageBaseUrl()))
              .toList();
    } catch (Exception e) {
      throw new ExternalApiException("TMDB tv API request failed.", e);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
