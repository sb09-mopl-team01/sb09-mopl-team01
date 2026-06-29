package io.mopl.domain.content.entity;

import io.mopl.global.entity.BaseUpdatableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "contents",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_contents_source_external_id",
            columnNames = {"source", "external_id"}
        )
    },
    indexes = {
        @Index(name = "idx_contents_type", columnList = "type"),
        @Index(name = "idx_contents_created_at_id", columnList = "created_at, id")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content extends BaseUpdatableEntity {

  @Column(nullable = false, length = 20)
  private ContentType type;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, length = 2000)
  private String description;

  @Column(name = "thumbnail_url", length = 2048)
  private String thumbnailUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private ContentSource source;

  @Column(name = "external_id", length = 100)
  private String externalId;

  @Column(name = "last_synced_at")
  private Instant lastSyncedAt;

  @ElementCollection(fetch = FetchType.LAZY)
  @CollectionTable(
      name = "content_tags",
      joinColumns = @JoinColumn(name = "content_id"),
      uniqueConstraints = {
          @UniqueConstraint(
              name = "uk_content_tags_content_id_tag",
              columnNames = {"content_id", "tag"}
          )
      },
      indexes = {
          @Index(name = "idx_content_tags_tag_content_id", columnList = "tag, content_id")
      }
  )
  @Column(name = "tag", nullable = false, length = 50)
  private Set<String> tags = new LinkedHashSet<>();

  private Content(
      ContentType type,
      String title,
      String description,
      String thumbnailUrl,
      ContentSource source,
      String externalId,
      Instant lastSyncedAt,
      Collection<String> tags
  ) {
    validateExternalId(source, externalId);
    this.type = Objects.requireNonNull(type, "콘텐츠 타입은 필수입니다.");
    this.title = requireText(title, "콘텐츠 제목은 필수입니다.");
    this.description = requireText(description, "콘텐츠 설명은 필수입니다.");
    this.thumbnailUrl = normalizeNullableText(thumbnailUrl);
    this.source = Objects.requireNonNull(source, "콘텐츠 출처는 필수입니다.");
    this.externalId = normalizeNullableText(externalId);
    this.lastSyncedAt = lastSyncedAt;
    this.tags = normalizeTags(tags);
  }

  public static Content createManual(
      ContentType type,
      String title,
      String description,
      String thumbnailUrl,
      Collection<String> tags
  ) {
    return new Content(type, title, description, thumbnailUrl, ContentSource.MANUAL, null, null, tags);
  }

  public static Content createExternal(
      ContentType type,
      String title,
      String description,
      String thumbnailUrl,
      ContentSource source,
      String externalId,
      Instant lastSyncedAt,
      Collection<String> tags
  ) {
    if (source == ContentSource.MANUAL) {
      throw new IllegalArgumentException("외부 콘텐츠는 MANUAL 출처를 사용할 수 없습니다.");
    }
    return new Content(type, title, description, thumbnailUrl, source, externalId, lastSyncedAt, tags);
  }

  public void markSyncedAt(Instant syncedAt) {
    if (!source.isExternal()) {
      throw new IllegalStateException("수동 등록 콘텐츠는 동기화 시각을 갱신할 수 없습니다.");
    }
    this.lastSyncedAt = Objects.requireNonNull(syncedAt, "동기화 시각은 필수입니다.");
  }

  private static void validateExternalId(ContentSource source, String externalId) {
    if (source == null) {
      return;
    }
    String normalizedExternalId = normalizeNullableText(externalId);
    if (source.isExternal() && normalizedExternalId == null) {
      throw new IllegalArgumentException("외부 콘텐츠는 externalId가 필수입니다.");
    }
    if (!source.isExternal() && normalizedExternalId != null) {
      throw new IllegalArgumentException("수동 등록 콘텐츠는 externalId를 가질 수 없습니다.");
    }
  }

  private static String requireText(String value, String message) {
    String normalized = normalizeNullableText(value);
    if (normalized == null) {
      throw new IllegalArgumentException(message);
    }
    return normalized;
  }

  private static String normalizeNullableText(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static Set<String> normalizeTags(Collection<String> tags) {
    if (tags == null) {
      throw new IllegalArgumentException("콘텐츠 태그는 필수입니다.");
    }

    Set<String> normalizedTags = new LinkedHashSet<>();
    for (String tag : tags) {
      String normalizedTag = requireText(tag, "콘텐츠 태그는 빈 값일 수 없습니다.");
      if (normalizedTag.length() > 50) {
        throw new IllegalArgumentException("콘텐츠 태그는 50자를 초과할 수 없습니다.");
      }
      normalizedTags.add(normalizedTag);
    }

    if (normalizedTags.isEmpty()) {
      throw new IllegalArgumentException("콘텐츠 태그는 하나 이상 필요합니다.");
    }
    return normalizedTags;
  }

  //리뷰: 리뷰 평점 평균 동기화 추가
  public void updateAverageRating(double newAverage) {
  }
}
