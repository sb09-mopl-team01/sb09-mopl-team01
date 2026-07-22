package io.mopl.domain.content.service;

import io.mopl.domain.content.document.ContentDocument;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.content.repository.search.ContentSearchRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenSearchContentDataInitializer {

  private static final int PAGE_SIZE = 500;

  private final ContentRepository contentRepository;
  private final ContentSearchRepository contentSearchRepository;
  private final ContentSearchIndexService contentSearchIndexService;
  private final ElasticsearchOperations elasticsearchOperations;

  @EventListener(ApplicationReadyEvent.class)
  public void initializeContentData() {
    try {
      IndexOperations indexOperations = elasticsearchOperations.indexOps(ContentDocument.class);
      if (!indexOperations.exists()) {
        indexOperations.createWithMapping();
        log.info("Content search index created. result=success, index=contents");
      }

      long currentCount = contentSearchRepository.count();
      if (currentCount > 0) {
        log.info(
            "Content search initialSync skipped. reason=indexNotEmpty, indexedCount={}",
            currentCount
        );
        return;
      }

      int pageNumber = 0;
      long indexedCount = 0L;
      Page<UUID> contentIdPage;
      do {
        contentIdPage = contentRepository.findActiveIds(PageRequest.of(pageNumber, PAGE_SIZE));
        List<UUID> contentIds = contentIdPage.getContent();
        contentSearchIndexService.indexAll(contentIds);
        indexedCount += contentIds.size();
        pageNumber++;
      } while (contentIdPage.hasNext());

      log.info(
          "Content search initialSync completed. result=success, indexedCount={}",
          indexedCount
      );
    } catch (RuntimeException e) {
      log.warn(
          "Content search initialSync failed. result=databaseFallback, errorType={}",
          e.getClass().getSimpleName()
      );
    }
  }
}
