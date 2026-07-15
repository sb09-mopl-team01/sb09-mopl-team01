package io.mopl.infra.external;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExternalContentApiPropertiesTest {

  @Test
  void timeout_rejectsNonPositiveValue() {
    assertThatThrownBy(() -> new ExternalContentApiProperties.Timeout(0, 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("외부 API timeout은 1초 이상이어야 합니다.");
  }

  @Test
  void retry_rejectsInvalidAttemptsAndBackoffRange() {
    assertThatThrownBy(() -> new ExternalContentApiProperties.Retry(0, 100, 2.0, 500))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("외부 API 최대 시도 횟수는 1회 이상이어야 합니다.");
    assertThatThrownBy(() -> new ExternalContentApiProperties.Retry(3, 500, 2.0, 100))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("외부 API 최대 backoff는 최초 backoff보다 작을 수 없습니다.");
    assertThatThrownBy(() -> new ExternalContentApiProperties.Retry(3, 100, Double.NaN, 500))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("외부 API backoff 배수는 1 이상이어야 합니다.");
  }

  @Test
  void tmdb_rejectsInvalidPageCount() {
    assertThatThrownBy(() -> new ExternalContentApiProperties.Tmdb(
        null,
        "https://api.themoviedb.org/3",
        "https://image.tmdb.org/t/p/w500",
        "/movie/popular",
        "/tv/popular",
        "ko-KR",
        "KR",
        true,
        0
    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TMDB 수집 page 수는 1 이상이어야 합니다.");
  }

  @Test
  void sportsDb_rejectsBlankEventsPath() {
    assertThatThrownBy(() -> new ExternalContentApiProperties.TheSportsDb(
        null,
        "https://www.thesportsdb.com/api/v1/json",
        null,
        " "
    ))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TheSportsDB events path은(는) 필수입니다.");
  }
}
