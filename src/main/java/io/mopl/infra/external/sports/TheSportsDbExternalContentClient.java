package io.mopl.infra.external.sports;

import io.mopl.infra.external.ExternalApiException;
import io.mopl.infra.external.ExternalApiRetryExecutor;
import io.mopl.infra.external.ExternalContentApiProperties;
import io.mopl.infra.external.ExternalContentCandidate;
import io.mopl.infra.external.ExternalContentClient;
import io.mopl.infra.external.ExternalContentFetchResult;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Slf4j
public class TheSportsDbExternalContentClient implements ExternalContentClient {

  private final RestClient restClient;
  private final ExternalContentApiProperties.TheSportsDb properties;
  private final TheSportsDbContentMapper theSportsDbContentMapper;
  private final ExternalApiRetryExecutor retryExecutor;

  public TheSportsDbExternalContentClient(
      RestClient.Builder restClientBuilder,
      ExternalContentApiProperties properties,
      TheSportsDbContentMapper theSportsDbContentMapper,
      ExternalApiRetryExecutor retryExecutor
  ) {
    this.properties = properties.theSportsDb();
    this.restClient = restClientBuilder.baseUrl(this.properties.baseUrl()).build();
    this.theSportsDbContentMapper = theSportsDbContentMapper;
    this.retryExecutor = retryExecutor;
  }

  @Override
  public ExternalContentFetchResult fetchContents() {
    if (isBlank(properties.apiKey())) {
      throw new ExternalApiException("TheSportsDB API key is required.");
    }
    if (isBlank(properties.leagueId())) {
      throw new ExternalApiException("TheSportsDB league id is required.");
    }

    List<TheSportsDbEventItem> events = retryExecutor.execute(
        "TheSportsDB events",
        this::requestEvents
    );

    List<ExternalContentCandidate> candidates = new ArrayList<>();
    int failedCount = 0;
    for (TheSportsDbEventItem event : events) {
      try {
        ExternalContentCandidate candidate = theSportsDbContentMapper.toCandidate(event);
        if (candidate == null) {
          failedCount++;
        } else {
          candidates.add(candidate);
        }
      } catch (RuntimeException e) {
        failedCount++;
        log.warn(
            "Content external item mapping failed. client=TheSportsDB, errorType={}, message={}",
            e.getClass().getSimpleName(),
            e.getMessage()
        );
      }
    }
    return new ExternalContentFetchResult(events.size(), candidates, 0, failedCount);
  }

  private List<TheSportsDbEventItem> requestEvents() {
    try {
      TheSportsDbEventsResponse response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path(properties.eventsPath().replace("{apiKey}", properties.apiKey()))
              .queryParam("id", properties.leagueId())
              .build())
          .retrieve()
          .body(TheSportsDbEventsResponse.class);
      return response == null || response.events() == null ? List.of() : response.events();
    } catch (RestClientException e) {
      throw ExternalApiException.fromRestClientFailure("TheSportsDB API request failed.", e);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
