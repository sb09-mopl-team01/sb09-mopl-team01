package io.mopl.infra.external.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.infra.external.ExternalContentCandidate;
import org.junit.jupiter.api.Test;

class TmdbContentMapperTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final TmdbContentMapper mapper = new TmdbContentMapper();

  @Test
  void deserializeMovieResponseAndConvertToCandidate() throws Exception {
    String json = """
        {
          "results": [
            {
              "id": 550,
              "title": "Fight Club",
              "overview": "A ticking-time-bomb insomniac...",
              "poster_path": "/poster.jpg",
              "backdrop_path": "/backdrop.jpg",
              "genre_ids": [18, 53, 18],
              "release_date": "1999-10-15"
            }
          ]
        }
        """;

    TmdbContentResponse<TmdbMovieItem> response = objectMapper.readValue(
        json,
        new TypeReference<>() {
        }
    );

    ExternalContentCandidate candidate = mapper.toMovieCandidate(
        response.results().get(0),
        "https://image.tmdb.org/t/p/w500"
    );

    assertThat(candidate.type()).isEqualTo(ContentType.MOVIE);
    assertThat(candidate.source()).isEqualTo(ContentSource.TMDB);
    assertThat(candidate.externalId()).isEqualTo("550");
    assertThat(candidate.title()).isEqualTo("Fight Club");
    assertThat(candidate.description()).isEqualTo("A ticking-time-bomb insomniac...");
    assertThat(candidate.thumbnailUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/poster.jpg");
    assertThat(candidate.tags()).containsExactly("genre:18", "genre:53");
  }

  @Test
  void deserializeTvResponseAndConvertToCandidate() throws Exception {
    String json = """
        {
          "results": [
            {
              "id": 1399,
              "name": "Game of Thrones",
              "overview": "Seven noble families fight...",
              "poster_path": null,
              "backdrop_path": "/backdrop.jpg",
              "genre_ids": [10765, 18],
              "first_air_date": "2011-04-17"
            }
          ]
        }
        """;

    TmdbContentResponse<TmdbTvItem> response = objectMapper.readValue(
        json,
        new TypeReference<>() {
        }
    );

    ExternalContentCandidate candidate = mapper.toTvCandidate(
        response.results().get(0),
        "https://image.tmdb.org/t/p/w500/"
    );

    assertThat(candidate.type()).isEqualTo(ContentType.TV_SERIES);
    assertThat(candidate.source()).isEqualTo(ContentSource.TMDB);
    assertThat(candidate.externalId()).isEqualTo("1399");
    assertThat(candidate.title()).isEqualTo("Game of Thrones");
    assertThat(candidate.description()).isEqualTo("Seven noble families fight...");
    assertThat(candidate.thumbnailUrl()).isEqualTo("https://image.tmdb.org/t/p/w500/backdrop.jpg");
    assertThat(candidate.tags()).containsExactly("genre:10765", "genre:18");
  }
}
