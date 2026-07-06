package io.mopl.infra.external.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TmdbTvItem(
    Long id,
    String name,
    String overview,
    @JsonProperty("poster_path")
    String posterPath,
    @JsonProperty("backdrop_path")
    String backdropPath,
    @JsonProperty("genre_ids")
    List<Integer> genreIds,
    @JsonProperty("first_air_date")
    String firstAirDate
) {
}
