package io.mopl.global.logging;

import java.io.IOException;
import java.nio.file.Path;

public interface LogArchiveStorage {

  boolean exists(String objectKey);

  void upload(Path archive, String objectKey) throws IOException;
}
