package io.mopl.domain.content.service;

import io.mopl.domain.content.document.ContentDocument;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.content.repository.search.ContentSearchRepository;
import io.mopl.domain.watchingsession.repository.WatchingSessionRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContentSearchIndexService {

  private final ContentRepository contentRepository;
  private final ContentSearchRepository contentSearchRepository;
  private final WatchingSessionRepository watchingSessionRepository;
  private final ElasticsearchOperations elasticsearchOperations;

  public void index(UUID contentId) {
    indexAll(List.of(contentId));
  }

  public void indexAll(Collection<UUID> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return;
    }

    try {
      List<UUID> distinctContentIds = List.copyOf(new LinkedHashSet<>(contentIds));
      Map<UUID, Long> watcherCounts = watchingSessionRepository.countByContentIds(
          distinctContentIds
      );
      List<ContentDocument> documents = contentRepository.findAllByIdWithTags(
              distinctContentIds
          ).stream()
          .map(content -> ContentDocument.from(
              content,
              watcherCounts.getOrDefault(content.getId(), 0L)
          ))
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

  public void synchronizeWatcherCount(UUID contentId) {
    if (contentId == null) {
      return;
    }

    try {
      WatcherCountSyncResult result = synchronizeWatcherCounts(List.of(contentId));
      log.debug(
          "Content search watcherCount sync completed. operation=partialUpdate, result=success, "
              + "processed={}, updated={}, indexed={}, unchanged={}",
          result.processedCount(),
          result.updatedCount(),
          result.indexedCount(),
          result.unchangedCount()
      );
    } catch (RuntimeException e) {
      log.warn(
          "Content search watcherCount sync failed. operation=partialUpdate, result=deferred, "
              + "count=1, errorType={}",
          e.getClass().getSimpleName()
      );
    }
  }

  public WatcherCountSyncResult synchronizeWatcherCounts(Collection<UUID> contentIds) {
    if (contentIds == null || contentIds.isEmpty()) {
      return WatcherCountSyncResult.empty();
    }

    List<UUID> distinctContentIds = List.copyOf(new LinkedHashSet<>(contentIds));
    Map<UUID, Long> watcherCounts = watchingSessionRepository.countByContentIds(
        distinctContentIds
    );
    Map<UUID, ContentDocument> documentsById = StreamSupport.stream(
            contentSearchRepository.findAllById(distinctContentIds).spliterator(),
            false
        )
        .collect(Collectors.toMap(ContentDocument::getId, Function.identity()));

    List<UpdateQuery> updateQueries = new ArrayList<>();
    List<UUID> missingDocumentIds = new ArrayList<>();
    int unchangedCount = 0;

    for (UUID contentId : distinctContentIds) {
      long watcherCount = watcherCounts.getOrDefault(contentId, 0L);
      ContentDocument document = documentsById.get(contentId);
      if (document == null) {
        missingDocumentIds.add(contentId);
      } else if (document.getWatcherCount() == watcherCount) {
        unchangedCount++;
      } else {
        updateQueries.add(UpdateQuery.builder(contentId.toString())
            .withDocument(Document.from(Map.of("watcherCount", watcherCount)))
            .withRetryOnConflict(3)
            .build());
      }
    }

    if (!updateQueries.isEmpty()) {
      elasticsearchOperations.bulkUpdate(updateQueries, ContentDocument.class);
    }

    int indexedCount = indexMissingDocuments(missingDocumentIds, watcherCounts);
    return new WatcherCountSyncResult(
        distinctContentIds.size(),
        updateQueries.size(),
        indexedCount,
        unchangedCount
    );
  }

  private int indexMissingDocuments(
      Collection<UUID> contentIds,
      Map<UUID, Long> watcherCounts
  ) {
    if (contentIds.isEmpty()) {
      return 0;
    }

    List<ContentDocument> documents = contentRepository.findAllByIdWithTags(contentIds).stream()
        .map(content -> ContentDocument.from(
            content,
            watcherCounts.getOrDefault(content.getId(), 0L)
        ))
        .toList();
    if (!documents.isEmpty()) {
      contentSearchRepository.saveAll(documents);
    }
    return documents.size();
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

  public record WatcherCountSyncResult(
      int processedCount,
      int updatedCount,
      int indexedCount,
      int unchangedCount
  ) {

    public static WatcherCountSyncResult empty() {
      return new WatcherCountSyncResult(0, 0, 0, 0);
    }
  }
}
