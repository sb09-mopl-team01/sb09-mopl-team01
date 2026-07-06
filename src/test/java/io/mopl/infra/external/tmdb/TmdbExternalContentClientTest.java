package io.mopl.infra.external.tmdb;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import io.mopl.infra.external.ExternalApiException;
import io.mopl.infra.external.ExternalContentApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TmdbExternalContentClientTest {

  @Test
  void fetchContents_wrapsApiResponseError() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    TmdbExternalContentClient client = new TmdbExternalContentClient(
        restClientBuilder,
        properties(),
        new TmdbContentMapper()
    );
    mockServer.expect(once(), requestTo("https://api.themoviedb.org/3/movie/popular?api_key=test-api-key&page=1"))
        .andRespond(withServerError());

    assertThatThrownBy(client::fetchContents)
        .isInstanceOf(ExternalApiException.class)
        .hasMessage("TMDB movie API request failed.")
        .hasCauseInstanceOf(RuntimeException.class);

    mockServer.verify();
  }

  private static ExternalContentApiProperties properties() {
    return new ExternalContentApiProperties(
        new ExternalContentApiProperties.Timeout(3, 5),
        new ExternalContentApiProperties.Tmdb(
            "test-api-key",
            "https://api.themoviedb.org/3",
            "https://image.tmdb.org/t/p/w500",
            "/movie/popular",
            "/tv/popular",
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
