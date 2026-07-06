package io.mopl.infra.external;

import java.util.List;

public interface ExternalContentClient {

  List<ExternalContentCandidate> fetchContents();
}
