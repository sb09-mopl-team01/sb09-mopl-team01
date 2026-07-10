package io.mopl.infra.external.sports;

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
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TheSportsDbExternalContentClientTest {

  @Test
  void fetchContents_countsUnmappableItemAndKeepsValidItem() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    ExternalContentApiProperties properties = properties();
    TheSportsDbExternalContentClient client = new TheSportsDbExternalContentClient(
        restClientBuilder,
        properties,
        new TheSportsDbContentMapper(),
        new ExternalApiRetryExecutor(properties)
    );
    mockServer.expect(once(), requestTo(
            "https://www.thesportsdb.com/api/v1/json/test-sports-key/eventsnextleague.php?id=4328"))
        .andRespond(withSuccess("""
            {
              "events": [
                {
                  "idEvent": "event-1",
                  "strEvent": "Arsenal vs Chelsea",
                  "strSport": "Soccer",
                  "strLeague": "English Premier League",
                  "strDescriptionEN": "League match"
                },
                null
              ]
            }
            """, MediaType.APPLICATION_JSON));

    ExternalContentFetchResult result = client.fetchContents();

    assertThat(result.fetchedCount()).isEqualTo(2);
    assertThat(result.acceptedCount()).isEqualTo(1);
    assertThat(result.filteredCount()).isZero();
    assertThat(result.failedCount()).isEqualTo(1);
    assertThat(result.candidates())
        .extracting(ExternalContentCandidate::externalId)
        .containsExactly("event-1");
    mockServer.verify();
  }

  @Test
  void fetchContents_wrapsApiResponseError() {
    RestClient.Builder restClientBuilder = RestClient.builder();
    MockRestServiceServer mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
    ExternalContentApiProperties properties = properties();
    TheSportsDbExternalContentClient client = new TheSportsDbExternalContentClient(
        restClientBuilder,
        properties,
        new TheSportsDbContentMapper(),
        new ExternalApiRetryExecutor(properties)
    );
    mockServer.expect(once(), requestTo(
            "https://www.thesportsdb.com/api/v1/json/test-sports-key/eventsnextleague.php?id=4328"))
        .andRespond(withServerError());

    assertThatThrownBy(client::fetchContents)
        .isInstanceOf(ExternalApiException.class)
        .hasMessage("TheSportsDB API request failed.")
        .hasCauseInstanceOf(RuntimeException.class);

    mockServer.verify();
  }

  private static ExternalContentApiProperties properties() {
    return new ExternalContentApiProperties(
        new ExternalContentApiProperties.Timeout(3, 5),
        new ExternalContentApiProperties.Retry(1, 0, 1.0, 0),
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
            "test-sports-key",
            "https://www.thesportsdb.com/api/v1/json",
            "4328",
            "/{apiKey}/eventsnextleague.php"
        )
    );
  }
}
