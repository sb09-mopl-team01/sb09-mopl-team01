package io.mopl.infra.external.tmdb;

import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.infra.external.ExternalContentCandidate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class TmdbContentMapper {

  public ExternalContentCandidate toMovieCandidate(TmdbMovieItem item, String imageBaseUrl) {
    if (item == null) {
      return null;
    }
    return new ExternalContentCandidate(
        ContentType.MOVIE,
        ContentSource.TMDB,
        toExternalId(item.id()),
        item.title(),
        item.overview(),
        toImageUrl(imageBaseUrl, item.posterPath(), item.backdropPath()),
        toGenreTags(item.genreIds())
    );
  }

  public ExternalContentCandidate toTvCandidate(TmdbTvItem item, String imageBaseUrl) {
    if (item == null) {
      return null;
    }
    return new ExternalContentCandidate(
        ContentType.TV_SERIES,
        ContentSource.TMDB,
        toExternalId(item.id()),
        item.name(),
        item.overview(),
        toImageUrl(imageBaseUrl, item.posterPath(), item.backdropPath()),
        toGenreTags(item.genreIds())
    );
  }

  private static List<String> toGenreTags(Collection<Integer> genreIds) {
    if (genreIds == null || genreIds.isEmpty()) {
      return List.of();
    }
    return genreIds.stream()
        .filter(Objects::nonNull)
        .distinct()
        .map(genreId -> "genre:" + genreId)
        .toList();
  }

  private static String toExternalId(Long id) {
    return id == null ? null : String.valueOf(id);
  }

  private static String toImageUrl(String imageBaseUrl, String posterPath, String backdropPath) {
    String imagePath = firstNotBlank(posterPath, backdropPath);
    if (imagePath == null) {
      return null;
    }
    return normalizeBaseUrl(imageBaseUrl) + imagePath;
  }

  private static String firstNotBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    if (second != null && !second.isBlank()) {
      return second;
    }
    return null;
  }

  private static String normalizeBaseUrl(String imageBaseUrl) {
    if (imageBaseUrl == null || imageBaseUrl.isBlank()) {
      return "";
    }
    return imageBaseUrl.endsWith("/") ? imageBaseUrl.substring(0, imageBaseUrl.length() - 1) : imageBaseUrl;
  }
}
