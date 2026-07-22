package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.document.ContentDocument;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.content.repository.search.ContentSearchRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

@ExtendWith(MockitoExtension.class)
class OpenSearchContentDataInitializerTest {

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ContentSearchRepository contentSearchRepository;

  @Mock
  private ContentSearchIndexService contentSearchIndexService;

  @Mock
  private ElasticsearchOperations elasticsearchOperations;

  @Mock
  private IndexOperations indexOperations;

  private OpenSearchContentDataInitializer initializer;

  @BeforeEach
  void setUp() {
    initializer = new OpenSearchContentDataInitializer(
        contentRepository,
        contentSearchRepository,
        contentSearchIndexService,
        elasticsearchOperations
    );
    given(elasticsearchOperations.indexOps(ContentDocument.class)).willReturn(indexOperations);
  }

  @Test
  void indexesActiveDatabaseContentsWhenIndexIsEmpty() {
    List<UUID> contentIds = List.of(UUID.randomUUID(), UUID.randomUUID());
    given(contentSearchRepository.count()).willReturn(0L);
    given(contentRepository.findActiveIds(Pageable.ofSize(500)))
        .willReturn(new PageImpl<>(contentIds));

    initializer.initializeContentData();

    verify(contentSearchIndexService).indexAll(contentIds);
    verify(indexOperations).createWithMapping();
  }

  @Test
  void skipsInitializationWhenIndexAlreadyContainsDocuments() {
    given(indexOperations.exists()).willReturn(true);
    given(contentSearchRepository.count()).willReturn(3L);

    initializer.initializeContentData();

    verify(contentRepository, never()).findActiveIds(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void doesNotPreventApplicationStartupWhenOpenSearchIsUnavailable() {
    given(indexOperations.exists()).willThrow(new RuntimeException("unavailable"));

    assertThatCode(initializer::initializeContentData).doesNotThrowAnyException();
  }
}
