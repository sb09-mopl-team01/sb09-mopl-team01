package io.mopl.domain.content.entity;

public enum ContentSource {
  TMDB,
  THE_SPORTS_DB,
  MANUAL;

  public boolean isExternal() {
    return this != MANUAL;
  }
}
