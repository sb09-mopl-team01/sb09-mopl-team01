package io.mopl.domain.content.document;

import io.mopl.domain.content.entity.Content;
import io.mopl.global.util.InitialUtils;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

@Getter
@Builder
@Document(indexName = "contents", createIndex = false)
@Setting(settingPath = "/opensearch/content-index-settings.json")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ContentDocument {

  @Id
  @Field(type = FieldType.Keyword)
  private UUID id;

  @Field(
      type = FieldType.Text,
      analyzer = "content_ngram_analyzer",
      searchAnalyzer = "content_search_analyzer"
  )
  private String title;

  @Field(
      type = FieldType.Text,
      analyzer = "content_ngram_analyzer",
      searchAnalyzer = "content_search_analyzer"
  )
  private String description;

  @Field(type = FieldType.Keyword)
  private String initials;

  @Field(type = FieldType.Keyword)
  private String type;

  @Field(type = FieldType.Keyword)
  @Builder.Default
  private Set<String> tags = new LinkedHashSet<>();

  @Field(type = FieldType.Date)
  private Instant createdAt;

  @Field(type = FieldType.Double)
  private double averageRating;

  @Field(type = FieldType.Integer)
  private int reviewCount;

  @Field(type = FieldType.Long)
  private long watcherCount;

  public static ContentDocument from(Content content, long watcherCount) {
    return ContentDocument.builder()
        .id(content.getId())
        .title(content.getTitle())
        .description(content.getDescription())
        .initials(extractKoreanInitials(content.getTitle()))
        .type(content.getType().getValue())
        .tags(new LinkedHashSet<>(content.getTags()))
        .createdAt(content.getCreatedAt())
        .averageRating(content.getAverageRating())
        .reviewCount(content.getReviewCount())
        .watcherCount(watcherCount)
        .build();
  }

  private static String extractKoreanInitials(String title) {
    return InitialUtils.extractInitial(title).replaceAll("[^\\u3131-\\u314E]", "");
  }
}
