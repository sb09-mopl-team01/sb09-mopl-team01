package io.mopl.infra.external.tmdb;

import java.util.List;

public record TmdbContentResponse<T>(
    List<T> results
) {
}
