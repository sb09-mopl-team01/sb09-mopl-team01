package io.mopl.domain.user.repository.search;

import io.mopl.domain.user.document.UserDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface UserSearchRepository extends ElasticsearchRepository<UserDocument, UUID>, UserSearchRepositoryCustom {
  List<UserDocument> findByName(String name);
}
