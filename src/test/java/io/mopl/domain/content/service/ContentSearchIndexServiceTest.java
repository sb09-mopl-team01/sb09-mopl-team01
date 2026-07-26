package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.document.ContentDocument;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.content.repository.search.ContentSearchRepository;
import io.mopl.domain.watchingsession.repository.WatchingSessionRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentSearchIndexServiceTest {

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ContentSearchRepository contentSearchRepository;

  @Mock
  private WatchingSessionRepository watchingSessionRepository;

  @Mock
  private ElasticsearchOperations elasticsearchOperations;

  private ContentSearchIndexService contentSearchIndexService;

  @BeforeEach
  void setUp() {
    contentSearchIndexService = new ContentSearchIndexService(
        contentRepository,
        contentSearchRepository,
        watchingSessionRepository,
        elasticsearchOperations
    );
  }

  @Test
  void indexesDocumentsLoadedFromDatabase() {
    UUID contentId = UUID.randomUUID();
    Content content = Content.createManual(
        ContentType.MOVIE, "한국 영화", "설명", null, Set.of("드라마")
    );
    ReflectionTestUtils.setField(content, "id", contentId);
    content.updateReviewStats(4.5, 7);
    given(contentRepository.findAllByIdWithTags(List.of(contentId))).willReturn(List.of(content));
    given(watchingSessionRepository.countByContentIds(List.of(contentId)))
        .willReturn(Map.of(contentId, 3L));

    contentSearchIndexService.index(contentId);

    verify(contentSearchRepository).saveAll(argThat(documents -> {
      io.mopl.domain.content.document.ContentDocument document = documents.iterator().next();
      return document.getReviewCount() == 7
          && document.getAverageRating() == 4.5
          && document.getWatcherCount() == 3L;
    }));
  }

  @Test
  void ignoresEmptyIndexRequest() {
    contentSearchIndexService.indexAll(List.of());

    verify(contentRepository, never()).findAllByIdWithTags(any());
  }

  @Test
  void keepsDatabaseRequestSuccessfulWhenOpenSearchFails() {
    UUID contentId = UUID.randomUUID();
    Content content = Content.createManual(
        ContentType.MOVIE, "한국 영화", "설명", null, Set.of("드라마")
    );
    ReflectionTestUtils.setField(content, "id", contentId);
    given(contentRepository.findAllByIdWithTags(List.of(contentId))).willReturn(List.of(content));
    given(watchingSessionRepository.countByContentIds(List.of(contentId)))
        .willReturn(Map.of());
    given(contentSearchRepository.saveAll(any())).willThrow(new RuntimeException("unavailable"));

    assertThatCode(() -> contentSearchIndexService.index(contentId)).doesNotThrowAnyException();
  }

  @Test
  void keepsDatabaseDeleteSuccessfulWhenOpenSearchFails() {
    UUID contentId = UUID.randomUUID();
    org.mockito.Mockito.doThrow(new RuntimeException("unavailable"))
        .when(contentSearchRepository).deleteById(contentId);

    assertThatCode(() -> contentSearchIndexService.delete(contentId)).doesNotThrowAnyException();
  }

  @Test
  void partiallyUpdatesWatcherCountForExistingDocument() {
    UUID contentId = UUID.randomUUID();
    ContentDocument document = ContentDocument.builder()
        .id(contentId)
        .watcherCount(1L)
        .build();
    given(watchingSessionRepository.countByContentIds(List.of(contentId)))
        .willReturn(Map.of(contentId, 3L));
    given(contentSearchRepository.findAllById(List.of(contentId)))
        .willReturn(List.of(document));

    ContentSearchIndexService.WatcherCountSyncResult result =
        contentSearchIndexService.synchronizeWatcherCounts(List.of(contentId));

    ArgumentCaptor<List<UpdateQuery>> updates = ArgumentCaptor.forClass(List.class);
    verify(elasticsearchOperations).bulkUpdate(updates.capture(), eq(ContentDocument.class));
    UpdateQuery update = updates.getValue().get(0);
    org.assertj.core.api.Assertions.assertThat(update.getDocument().get("watcherCount"))
        .isEqualTo(3L);
    org.assertj.core.api.Assertions.assertThat(result.updatedCount()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(result.indexedCount()).isZero();
  }

  @Test
  void indexesMissingDocumentWithCurrentWatcherCount() {
    UUID contentId = UUID.randomUUID();
    Content content = Content.createManual(
        ContentType.MOVIE, "한국 영화", "설명", null, Set.of("드라마")
    );
    ReflectionTestUtils.setField(content, "id", contentId);
    given(watchingSessionRepository.countByContentIds(List.of(contentId)))
        .willReturn(Map.of(contentId, 4L));
    given(contentSearchRepository.findAllById(List.of(contentId))).willReturn(List.of());
    given(contentRepository.findAllByIdWithTags(List.of(contentId))).willReturn(List.of(content));

    ContentSearchIndexService.WatcherCountSyncResult result =
        contentSearchIndexService.synchronizeWatcherCounts(List.of(contentId));

    verify(contentSearchRepository).saveAll(argThat(documents ->
        documents.iterator().next().getWatcherCount() == 4L
    ));
    org.assertj.core.api.Assertions.assertThat(result.indexedCount()).isEqualTo(1);
    verify(elasticsearchOperations, never()).bulkUpdate(any(), any(Class.class));
  }
}
