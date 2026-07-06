package io.mopl.infra.external.sports;

import io.mopl.infra.external.ExternalApiException;
import io.mopl.infra.external.ExternalContentApiProperties;
import io.mopl.infra.external.ExternalContentCandidate;
import io.mopl.infra.external.ExternalContentClient;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TheSportsDbExternalContentClient implements ExternalContentClient {

  private final RestClient restClient;
  private final ExternalContentApiProperties.TheSportsDb properties;
  private final TheSportsDbContentMapper theSportsDbContentMapper;

  public TheSportsDbExternalContentClient(
      RestClient.Builder restClientBuilder,
      ExternalContentApiProperties properties,
      TheSportsDbContentMapper theSportsDbContentMapper
  ) {
    this.properties = properties.theSportsDb();
    this.restClient = restClientBuilder.baseUrl(this.properties.baseUrl()).build();
    this.theSportsDbContentMapper = theSportsDbContentMapper;
  }

  @Override
  public List<ExternalContentCandidate> fetchContents() {
    if (isBlank(properties.apiKey())) {
      throw new ExternalApiException("TheSportsDB API key is required.");
    }
    if (isBlank(properties.leagueId())) {
      throw new ExternalApiException("TheSportsDB league id is required.");
    }

    try {
      TheSportsDbEventsResponse response = restClient.get()
          .uri(uriBuilder -> uriBuilder
              .path(properties.eventsPath().replace("{apiKey}", properties.apiKey()))
              .queryParam("id", properties.leagueId())
              .build())
          .retrieve()
          .body(TheSportsDbEventsResponse.class);

      return response == null || response.events() == null
          ? List.of()
          : response.events().stream()
              .map(theSportsDbContentMapper::toCandidate)
              .toList();
    } catch (Exception e) {
      throw new ExternalApiException("TheSportsDB API request failed.", e);
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
