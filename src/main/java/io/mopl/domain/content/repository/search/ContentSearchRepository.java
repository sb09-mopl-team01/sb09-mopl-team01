package io.mopl.domain.content.repository.search;

import io.mopl.domain.content.document.ContentDocument;
import java.util.UUID;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ContentSearchRepository extends
    ElasticsearchRepository<ContentDocument, UUID>, ContentSearchRepositoryCustom {
}
