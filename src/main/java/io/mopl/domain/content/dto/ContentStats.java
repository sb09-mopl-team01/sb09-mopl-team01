package io.mopl.domain.content.dto;

public record ContentStats(
    double averageRating,
    int reviewCount,
    long watcherCount
) {

  public static ContentStats empty() {
    return new ContentStats(0.0, 0, 0L);
  }
}
