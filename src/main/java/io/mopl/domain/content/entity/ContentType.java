package io.mopl.domain.content.entity;

import java.util.Arrays;

public enum ContentType {
  MOVIE("movie"),
  TV_SERIES("tvSeries"),
  SPORT("sport");

  private final String value;

  ContentType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static ContentType from(String value) {
    return Arrays.stream(values())
        .filter(type -> type.value.equals(value))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 콘텐츠 타입입니다: " + value));
  }
}
