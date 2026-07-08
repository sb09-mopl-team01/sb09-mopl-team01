package io.mopl.infra.external.tmdb;

import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.infra.external.ExternalContentCandidate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class TmdbContentMapper {

  private static final Map<Integer, String> GENRE_NAMES_BY_ID = Map.ofEntries(
      Map.entry(12, "\uBAA8\uD5D8"),
      Map.entry(14, "\uD310\uD0C0\uC9C0"),
      Map.entry(16, "\uC560\uB2C8\uBA54\uC774\uC158"),
      Map.entry(18, "\uB4DC\uB77C\uB9C8"),
      Map.entry(27, "\uACF5\uD3EC"),
      Map.entry(28, "\uC561\uC158"),
      Map.entry(35, "\uCF54\uBBF8\uB514"),
      Map.entry(36, "\uC5ED\uC0AC"),
      Map.entry(37, "\uC11C\uBD80"),
      Map.entry(53, "\uC2A4\uB9B4\uB7EC"),
      Map.entry(80, "\uBC94\uC8C4"),
      Map.entry(99, "\uB2E4\uD050\uBA58\uD130\uB9AC"),
      Map.entry(878, "SF"),
      Map.entry(9648, "\uBBF8\uC2A4\uD130\uB9AC"),
      Map.entry(10402, "\uC74C\uC545"),
      Map.entry(10749, "\uB85C\uB9E8\uC2A4"),
      Map.entry(10751, "\uAC00\uC871"),
      Map.entry(10752, "\uC804\uC7C1"),
      Map.entry(10759, "\uC561\uC158 & \uBAA8\uD5D8"),
      Map.entry(10762, "\uD0A4\uC988"),
      Map.entry(10763, "\uB274\uC2A4"),
      Map.entry(10764, "\uB9AC\uC5BC\uB9AC\uD2F0"),
      Map.entry(10765, "SF & \uD310\uD0C0\uC9C0"),
      Map.entry(10766, "\uC18C\uD504"),
      Map.entry(10767, "\uD1A0\uD06C"),
      Map.entry(10768, "\uC804\uC7C1 & \uC815\uCE58"),
      Map.entry(10770, "TV \uC601\uD654")
  );

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
        .map(GENRE_NAMES_BY_ID::get)
        .filter(Objects::nonNull)
        .collect(java.util.stream.Collectors.collectingAndThen(
            java.util.stream.Collectors.toCollection(LinkedHashSet::new),
            List::copyOf
        ));
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
