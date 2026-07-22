package io.mopl.domain.user.document;

import org.springframework.data.annotation.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;

@Getter
@Builder
@Document(indexName = "users")
public class UserDocument {

  @Id
  @Field(type = FieldType.Keyword)
  private UUID id;

  @Field(type = FieldType.Keyword)
  private String name;

  @MultiField(
      mainField = @Field(type = FieldType.Text, analyzer = "standard"),
      otherFields = {
          @InnerField(suffix = "keyword", type = FieldType.Keyword)
      }
  )
  private String email;

  @Field(type = FieldType.Keyword)
  private String role;

  @Field(type = FieldType.Boolean)
  private boolean isLocked;

  @Field(type = FieldType.Date)
  private Instant createdAt;
}
