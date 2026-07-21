package io.mopl.infra.external.tmdb;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TmdbMovieItem(
    Long id,
    String title,
    String overview,
    @JsonProperty("poster_path")
    String posterPath,
    @JsonProperty("backdrop_path")
    String backdropPath,
    @JsonProperty("genre_ids")
    List<Integer> genreIds,
    @JsonProperty("release_date")
    String releaseDate
) {
}
