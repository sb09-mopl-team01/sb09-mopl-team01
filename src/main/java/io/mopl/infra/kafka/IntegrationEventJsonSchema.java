package io.mopl.infra.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.confluent.kafka.schemaregistry.json.JsonSchema;
import java.io.IOException;
import lombok.Getter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 코드와 Kafka Schema Registry가 동일한 JSON Schema 계약을 사용하도록 제공합니다.
 */
@Getter
@Component
public class IntegrationEventJsonSchema {

  private static final String SCHEMA_PATH = "contracts/events/integration-event-envelope.json";

  private final JsonSchema schema;

  public IntegrationEventJsonSchema(ObjectMapper objectMapper) {
    try (var inputStream = new ClassPathResource(SCHEMA_PATH).getInputStream()) {
      this.schema = new JsonSchema(objectMapper.readTree(inputStream));
    } catch (IOException e) {
      throw new IllegalStateException("Integration event JSON Schema를 읽을 수 없습니다.", e);
    }
  }
}
