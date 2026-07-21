package io.mopl.domain.content.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ContentTypeConverter implements AttributeConverter<ContentType, String> {

  @Override
  public String convertToDatabaseColumn(ContentType attribute) {
    return attribute == null ? null : attribute.getValue();
  }

  @Override
  public ContentType convertToEntityAttribute(String dbData) {
    return dbData == null ? null : ContentType.from(dbData);
  }
}
