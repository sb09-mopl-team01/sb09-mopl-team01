package io.mopl.infra.external.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.mopl.infra.external.ExternalApiException;
import io.mopl.infra.external.ExternalApiRetryExecutor;
import io.mopl.infra.external.ExternalContentApiProperties;
import io.mopl.infra.external.ExternalContentCandidate;
import io.mopl.infra.external.ExternalContentFetchResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TmdbExternalContentClientTest {

  @Test
  void fetchContents_requestsKoreanLocaleAndKeepsKoreanItemsOnly() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    ExternalContentApiProperties properties = properties();
    TmdbExternalContentClient client = new TmdbExternalContentClient(
        restClientBuilder,
        properties,
        new TmdbContentMapper(),
        new ExternalApiRetryExecutor(properties)
    );
    mockServer.expect(once(), requestTo(
            "https://api.themoviedb.org/3/movie/popular?api_key=test-api-key&page=1&language=ko-KR&region=KR"))
        .andRespond(withSuccess("""
            {
              "results": [
                {
                  "id": 1,
                  "title": "한국 영화",
                  "overview": "한국어 설명입니다.",
                  "poster_path": "/ko-movie.jpg",
                  "backdrop_path": null,
                  "genre_ids": [18]
                },
                {
                  "id": 2,
                  "title": "English Movie",
                  "overview": "English overview only.",
                  "poster_path": "/en-movie.jpg",
                  "backdrop_path": null,
                  "genre_ids": [35]
                }
              ]
            }
            """, MediaType.APPLICATION_JSON));
    mockServer.expect(once(), requestTo(
            "https://api.themoviedb.org/3/tv/popular?api_key=test-api-key&page=1&language=ko-KR"))
        .andRespond(withSuccess("""
            {
              "results": [
                {
                  "id": 3,
                  "name": "Korean Title Fallback",
                  "overview": "한국어 설명이 있는 드라마입니다.",
                  "poster_path": null,
                  "backdrop_path": "/ko-tv.jpg",
                  "genre_ids": [10765]
                }
              ]
            }
            """, MediaType.APPLICATION_JSON));

    ExternalContentFetchResult result = client.fetchContents();

    assertThat(result.fetchedCount()).isEqualTo(3);
    assertThat(result.acceptedCount()).isEqualTo(2);
    assertThat(result.filteredCount()).isEqualTo(1);
    assertThat(result.failedCount()).isZero();
    assertThat(result.candidates())
        .extracting(ExternalContentCandidate::externalId)
        .containsExactly("1", "3");
    mockServer.verify();
  }

  @Test
  void fetchContents_wrapsApiResponseError() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    ExternalContentApiProperties properties = properties();
    TmdbExternalContentClient client = new TmdbExternalContentClient(
        restClientBuilder,
        properties,
        new TmdbContentMapper(),
        new ExternalApiRetryExecutor(properties)
    );
    mockServer.expect(once(), requestTo(
            "https://api.themoviedb.org/3/movie/popular?api_key=test-api-key&page=1&language=ko-KR&region=KR"))
        .andRespond(withServerError());

    assertThatThrownBy(client::fetchContents)
        .isInstanceOf(ExternalApiException.class)
        .hasMessage("TMDB movie API request failed.")
        .hasCauseInstanceOf(RuntimeException.class);

    mockServer.verify();
  }

  @Test
  void fetchContents_retriesServerErrorAndContinuesAfterRecovery() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    ExternalContentApiProperties properties = properties(2);
    TmdbExternalContentClient client = new TmdbExternalContentClient(
        restClientBuilder,
        properties,
        new TmdbContentMapper(),
        new ExternalApiRetryExecutor(properties)
    );
    String movieUrl =
        "https://api.themoviedb.org/3/movie/popular?api_key=test-api-key&page=1&language=ko-KR&region=KR";
    mockServer.expect(once(), requestTo(movieUrl)).andRespond(withServerError());
    mockServer.expect(once(), requestTo(movieUrl))
        .andRespond(withSuccess("{\"results\": []}", MediaType.APPLICATION_JSON));
    mockServer.expect(once(), requestTo(
            "https://api.themoviedb.org/3/tv/popular?api_key=test-api-key&page=1&language=ko-KR"))
        .andRespond(withSuccess("{\"results\": []}", MediaType.APPLICATION_JSON));

    ExternalContentFetchResult result = client.fetchContents();

    assertThat(result.fetchedCount()).isZero();
    assertThat(result.failedCount()).isZero();
    mockServer.verify();
  }

  @Test
  void fetchContents_countsMappingFailureAndContinuesWithRemainingItems() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    ExternalContentApiProperties properties = properties();
    TmdbContentMapper mapper = new TmdbContentMapper() {
      @Override
      public ExternalContentCandidate toMovieCandidate(TmdbMovieItem item, String imageBaseUrl) {
        if (item != null && Long.valueOf(2L).equals(item.id())) {
          throw new IllegalArgumentException("invalid movie item");
        }
        return super.toMovieCandidate(item, imageBaseUrl);
      }
    };
    TmdbExternalContentClient client = new TmdbExternalContentClient(
        restClientBuilder,
        properties,
        mapper,
        new ExternalApiRetryExecutor(properties)
    );
    mockServer.expect(once(), requestTo(
            "https://api.themoviedb.org/3/movie/popular?api_key=test-api-key&page=1&language=ko-KR&region=KR"))
        .andRespond(withSuccess("""
            {
              "results": [
                {
                  "id": 1,
                  "title": "정상 영화",
                  "overview": "정상 설명입니다.",
                  "genre_ids": [18]
                },
                {
                  "id": 2,
                  "title": "잘못된 영화",
                  "overview": "잘못된 설명입니다.",
                  "genre_ids": [18]
                }
              ]
            }
            """, MediaType.APPLICATION_JSON));
    mockServer.expect(once(), requestTo(
            "https://api.themoviedb.org/3/tv/popular?api_key=test-api-key&page=1&language=ko-KR"))
        .andRespond(withSuccess("{\"results\": []}", MediaType.APPLICATION_JSON));

    ExternalContentFetchResult result = client.fetchContents();

    assertThat(result.fetchedCount()).isEqualTo(2);
    assertThat(result.acceptedCount()).isEqualTo(1);
    assertThat(result.filteredCount()).isZero();
    assertThat(result.failedCount()).isEqualTo(1);
    assertThat(result.candidates())
        .extracting(ExternalContentCandidate::externalId)
        .containsExactly("1");
    mockServer.verify();
  }

  private static ExternalContentApiProperties properties() {
    return properties(1);
  }

  private static ExternalContentApiProperties properties(int maxAttempts) {
    return new ExternalContentApiProperties(
        new ExternalContentApiProperties.Timeout(3, 5),
        new ExternalContentApiProperties.Retry(maxAttempts, 0, 1.0, 0),
        new ExternalContentApiProperties.Tmdb(
            "test-api-key",
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
}
