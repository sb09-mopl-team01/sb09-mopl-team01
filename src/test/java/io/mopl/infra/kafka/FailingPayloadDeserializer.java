package io.mopl.infra.kafka;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * Embedded Kafka에서 Schema Registry 없이 ErrorHandlingDeserializer의 poll 단계 실패를 재현합니다.
 */
public class FailingPayloadDeserializer implements Deserializer<Object> {

  private static final byte INVALID_BINARY_MARKER = 0;

  @Override
  public Object deserialize(String topic, byte[] data) {
    if (data != null && data.length > 0 && data[0] == INVALID_BINARY_MARKER) {
      throw new SerializationException("테스트용 바이너리 역직렬화 실패");
    }
    return data == null ? null : new String(data, StandardCharsets.UTF_8);
  }
}
