package io.mopl.infra.external.sports;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.infra.external.ExternalContentCandidate;
import org.junit.jupiter.api.Test;

class TheSportsDbContentMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final TheSportsDbContentMapper mapper = new TheSportsDbContentMapper();

  @Test
  void deserializeEventResponseAndConvertToCandidate() throws Exception {
    String json = """
        {
          "events": [
            {
              "idEvent": "12345",
              "strEvent": "Arsenal vs Chelsea",
              "strEventAlternate": "Arsenal v Chelsea",
              "strSport": "Soccer",
              "strLeague": "English Premier League",
              "strHomeTeam": "Arsenal",
              "strAwayTeam": "Chelsea",
              "dateEvent": "2026-07-02",
              "strTime": "12:00:00",
              "strThumb": "https://example.com/thumb.jpg",
              "strPoster": "https://example.com/poster.jpg",
              "strBanner": "https://example.com/banner.jpg",
              "strSquare": "https://example.com/square.jpg",
              "strFanart": "https://example.com/fanart.jpg",
              "strHomeTeamBadge": "https://example.com/home-badge.png",
              "strAwayTeamBadge": "https://example.com/away-badge.png",
              "strLeagueBadge": "https://example.com/league-badge.png",
              "strDescriptionEN": "Premier League match"
            }
          ]
        }
        """;

    TheSportsDbEventsResponse response = objectMapper.readValue(json, TheSportsDbEventsResponse.class);

    ExternalContentCandidate candidate = mapper.toCandidate(response.events().get(0));

    assertThat(candidate.type()).isEqualTo(ContentType.SPORT);
    assertThat(candidate.source()).isEqualTo(ContentSource.THE_SPORTS_DB);
    assertThat(candidate.externalId()).isEqualTo("12345");
    assertThat(candidate.title()).isEqualTo("Arsenal vs Chelsea");
    assertThat(candidate.description()).isEqualTo("Premier League match");
    assertThat(candidate.thumbnailUrl()).isEqualTo("https://example.com/thumb.jpg");
    assertThat(candidate.tags()).containsExactly(
        "sport",
        "Soccer",
        "English Premier League",
        "Arsenal",
        "Chelsea"
    );
  }

  @Test
  void convertToCandidateUsesTeamsWhenEventNameIsMissing() {
    TheSportsDbEventItem event = new TheSportsDbEventItem(
        "event-1",
        null,
        null,
        "Basketball",
        "NBA",
        "Lakers",
        "Warriors",
        "2026-07-02",
        "19:00:00",
        null,
        "https://example.com/poster.jpg",
        null,
        null,
        null,
        null,
        null,
        null,
        null
    );

    ExternalContentCandidate candidate = mapper.toCandidate(event);

    assertThat(candidate.title()).isEqualTo("Lakers vs Warriors");
    assertThat(candidate.description()).isEqualTo("NBA / 2026-07-02 / 19:00:00");
    assertThat(candidate.thumbnailUrl()).isEqualTo("https://example.com/poster.jpg");
  }

  @Test
  void convertToCandidateUsesTeamBadgeWhenEventImagesAreMissing() {
    TheSportsDbEventItem event = new TheSportsDbEventItem(
        "event-2",
        "Arsenal vs Coventry City",
        null,
        "Soccer",
        "English Premier League",
        "Arsenal",
        "Coventry City",
        "2026-08-21",
        "19:00:00",
        "",
        "",
        "",
        "",
        null,
        "https://example.com/arsenal-badge.png",
        "https://example.com/coventry-badge.png",
        "https://example.com/premier-league-badge.png",
        null
    );

    ExternalContentCandidate candidate = mapper.toCandidate(event);

    assertThat(candidate.thumbnailUrl()).isEqualTo("https://example.com/arsenal-badge.png");
  }
}
