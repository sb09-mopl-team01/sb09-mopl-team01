package io.mopl.domain.content.service;

import io.mopl.domain.content.document.ContentDocument;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.content.repository.search.ContentSearchRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSearchIndexService {

  private final ContentRepository contentRepository;
  private final ContentSearchRepository contentSearchRepository;

  public void index(UUID contentId) {
    indexAll(List.of(contentId));
  }

  public void indexAll(Collection<UUID> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return;
    }

    try {
      List<ContentDocument> documents = contentRepository.findAllByIdWithTags(contentIds).stream()
          .map(ContentDocument::from)
          .toList();
      if (!documents.isEmpty()) {
        contentSearchRepository.saveAll(documents);
      }
      log.info(
          "Content search index completed. operation=upsert, result=success, count={}",
          documents.size()
      );
    } catch (RuntimeException e) {
      log.warn(
          "Content search index failed. operation=upsert, result=databaseFallback, "
              + "count={}, errorType={}",
          contentIds.size(),
          e.getClass().getSimpleName()
      );
    }
  }

  public void delete(UUID contentId) {
    try {
      contentSearchRepository.deleteById(contentId);
      log.info("Content search index completed. operation=delete, result=success, count=1");
    } catch (RuntimeException e) {
      log.warn(
          "Content search index failed. operation=delete, result=databaseFallback, "
              + "count=1, errorType={}",
          e.getClass().getSimpleName()
      );
    }
  }
}
