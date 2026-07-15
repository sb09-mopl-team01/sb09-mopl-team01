package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.dto.ExternalContentSyncResult;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.infra.external.ExternalApiException;
import io.mopl.infra.external.ExternalContentCandidate;
import io.mopl.infra.external.ExternalContentClient;
import io.mopl.infra.external.ExternalContentFetchResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;

@ExtendWith(MockitoExtension.class)
class ContentExternalSyncServiceTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-07-02T00:00:00Z");

  @Mock
  private ContentRepository contentRepository;

  @Test
  void syncExternalContents_createsNewContentAndSkipsDuplicatedContent() {
    ExternalContentClient client = () -> ExternalContentFetchResult.accepted(List.of(
        candidate("tmdb-1", "New Movie"),
        candidate("tmdb-2", "Existing Movie")
    ));
    Content existingContent = Content.createExternal(
        ContentType.MOVIE,
        "Existing Movie",
        "Existing description",
        null,
        ContentSource.TMDB,
        "tmdb-2",
        Instant.parse("2026-07-01T00:00:00Z"),
        List.of("movie", "tmdb")
    );
    ContentExternalSyncService service = serviceWith(client);
    given(contentRepository.findAllBySourceInAndTypeInAndExternalIdIn(
        anyCollection(), anyCollection(), anyCollection()))
        .willReturn(List.of(existingContent));
    given(contentRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

    ExternalContentSyncResult result = service.syncExternalContents();

    assertThat(result.fetchedCount()).isEqualTo(2);
    assertThat(result.acceptedCount()).isEqualTo(2);
    assertThat(result.filteredCount()).isZero();
    assertThat(result.createdCount()).isEqualTo(1);
    assertThat(result.skippedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isZero();
    assertThat(result.syncedAt()).isEqualTo(FIXED_NOW);
    assertThat(existingContent.getLastSyncedAt()).isEqualTo(FIXED_NOW);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Content>> contentCaptor = ArgumentCaptor.forClass(List.class);
    verify(contentRepository).saveAll(contentCaptor.capture());
    Content savedContent = contentCaptor.getValue().get(0);
    assertThat(savedContent.getSource()).isEqualTo(ContentSource.TMDB);
    assertThat(savedContent.getExternalId()).isEqualTo("tmdb-1");
    assertThat(savedContent.getLastSyncedAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void syncExternalContents_skipsInvalidItemAndContinues() {
    ExternalContentClient client = () -> ExternalContentFetchResult.accepted(List.of(
        candidate("tmdb-1", "New Movie"),
        new ExternalContentCandidate(
            ContentType.MOVIE,
            ContentSource.TMDB,
            null,
            "Invalid Movie",
            "Invalid description",
            null,
            List.of("movie", "tmdb")
        )
    ));
    ContentExternalSyncService service = serviceWith(client);
    given(contentRepository.findAllBySourceInAndTypeInAndExternalIdIn(
        anyCollection(), anyCollection(), anyCollection()))
        .willReturn(List.of());
    given(contentRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

    ExternalContentSyncResult result = service.syncExternalContents();

    assertThat(result.fetchedCount()).isEqualTo(2);
    assertThat(result.acceptedCount()).isEqualTo(2);
    assertThat(result.filteredCount()).isZero();
    assertThat(result.createdCount()).isEqualTo(1);
    assertThat(result.skippedCount()).isZero();
    assertThat(result.failedCount()).isEqualTo(1);
    verify(contentRepository).saveAll(anyList());
  }

  @Test
  void syncExternalContents_countsClientMappingFailuresAndPolicyFiltersSeparately() {
    ExternalContentClient client = () -> new ExternalContentFetchResult(
        3,
        List.of(candidate("tmdb-1", "New Movie")),
        1,
        1
    );
    ContentExternalSyncService service = serviceWith(client);
    given(contentRepository.findAllBySourceInAndTypeInAndExternalIdIn(
        anyCollection(), anyCollection(), anyCollection()))
        .willReturn(List.of());
    given(contentRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

    ExternalContentSyncResult result = service.syncExternalContents();

    assertThat(result.fetchedCount()).isEqualTo(3);
    assertThat(result.acceptedCount()).isEqualTo(1);
    assertThat(result.filteredCount()).isEqualTo(1);
    assertThat(result.createdCount()).isEqualTo(1);
    assertThat(result.skippedCount()).isZero();
    assertThat(result.failedCount()).isEqualTo(1);
  }

  @Test
  void syncExternalContents_skipsCandidateWithMissingTypeAndContinues() {
    ExternalContentCandidate missingType = new ExternalContentCandidate(
        null,
        ContentSource.TMDB,
        "tmdb-invalid",
        "Invalid Movie",
        "Invalid description",
        null,
        List.of("movie")
    );
    ExternalContentClient client = () -> ExternalContentFetchResult.accepted(List.of(
        missingType,
        candidate("tmdb-1", "New Movie")
    ));
    ContentExternalSyncService service = serviceWith(client);
    given(contentRepository.findAllBySourceInAndTypeInAndExternalIdIn(
        anyCollection(), anyCollection(), anyCollection()))
        .willReturn(List.of());
    given(contentRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

    ExternalContentSyncResult result = service.syncExternalContents();

    assertThat(result.createdCount()).isEqualTo(1);
    assertThat(result.failedCount()).isEqualTo(1);
  }

  @Test
  void syncExternalContents_deduplicatesCandidateKeysBeforeDatabaseSync() {
    ExternalContentCandidate first = candidate("tmdb-1", "First Movie");
    ExternalContentCandidate duplicated = candidate("tmdb-1", "Duplicated Movie");
    ExternalContentClient client = () -> ExternalContentFetchResult.accepted(
        List.of(first, duplicated)
    );
    ContentExternalSyncService service = serviceWith(client);
    given(contentRepository.findAllBySourceInAndTypeInAndExternalIdIn(
        anyCollection(), anyCollection(), anyCollection()))
        .willReturn(List.of());
    given(contentRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

    ExternalContentSyncResult result = service.syncExternalContents();

    assertThat(result.createdCount()).isEqualTo(1);
    assertThat(result.skippedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isZero();
    verify(contentRepository).findAllBySourceInAndTypeInAndExternalIdIn(
        anyCollection(), anyCollection(), anyCollection());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Content>> contentCaptor = ArgumentCaptor.forClass(List.class);
    verify(contentRepository).saveAll(contentCaptor.capture());
    assertThat(contentCaptor.getValue())
        .singleElement()
        .extracting(Content::getTitle)
        .isEqualTo("First Movie");
  }

  @Test
  void syncExternalContents_keepsTmdbMovieAndTvWithSameExternalId() {
    ExternalContentCandidate movie = candidate("100", "Movie");
    ExternalContentCandidate tvSeries = new ExternalContentCandidate(
        ContentType.TV_SERIES,
        ContentSource.TMDB,
        "100",
        "TV Series",
        "Description",
        "https://image.example.com/tv-100.jpg",
        List.of("tvSeries", "tmdb")
    );
    ExternalContentClient client = () -> ExternalContentFetchResult.accepted(
        List.of(movie, tvSeries)
    );
    ContentExternalSyncService service = serviceWith(client);
    given(contentRepository.findAllBySourceInAndTypeInAndExternalIdIn(
        anyCollection(), anyCollection(), anyCollection()))
        .willReturn(List.of());
    given(contentRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

    ExternalContentSyncResult result = service.syncExternalContents();

    assertThat(result.createdCount()).isEqualTo(2);
    assertThat(result.skippedCount()).isZero();
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Content>> contentCaptor = ArgumentCaptor.forClass(List.class);
    verify(contentRepository).saveAll(contentCaptor.capture());
    assertThat(contentCaptor.getValue())
        .extracting(Content::getType)
        .containsExactly(ContentType.MOVIE, ContentType.TV_SERIES);
  }

  @Test
  void syncExternalContents_stopsAtFailedChunkAfterCompletingPreviousChunk() {
    List<ExternalContentCandidate> candidates = IntStream
        .rangeClosed(1, ContentExternalSyncService.SYNC_CHUNK_SIZE + 1)
        .mapToObj(index -> candidate("tmdb-" + index, "Movie " + index))
        .toList();
    ExternalContentClient client = () -> ExternalContentFetchResult.accepted(candidates);
    ContentExternalSyncService service = serviceWith(client);
    IllegalStateException failure = new IllegalStateException("second chunk failed");
    given(contentRepository.findAllBySourceInAndTypeInAndExternalIdIn(
        anyCollection(), anyCollection(), anyCollection()))
        .willReturn(List.of())
        .willThrow(failure);
    given(contentRepository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

    assertThatThrownBy(service::syncExternalContents).isSameAs(failure);

    verify(contentRepository, times(2))
        .findAllBySourceInAndTypeInAndExternalIdIn(
            anyCollection(), anyCollection(), anyCollection());
    verify(contentRepository).saveAll(anyList());
  }

  @Test
  void syncExternalContents_propagatesDatabaseFailure() {
    ExternalContentClient client = () -> ExternalContentFetchResult.accepted(
        List.of(candidate("tmdb-1", "New Movie"))
    );
    ContentExternalSyncService service = serviceWith(client);
    IllegalStateException failure = new IllegalStateException("database failed");
    given(contentRepository.findAllBySourceInAndTypeInAndExternalIdIn(
        anyCollection(), anyCollection(), anyCollection()))
        .willThrow(failure);

    assertThatThrownBy(service::syncExternalContents).isSameAs(failure);

    verify(contentRepository, never()).saveAll(any());
  }

  @Test
  void syncExternalContents_propagatesExternalApiFailure() {
    ExternalContentClient client = () -> {
      throw new ExternalApiException("external api failed");
    };
    ContentExternalSyncService service = serviceWith(client);

    assertThatThrownBy(service::syncExternalContents)
        .isInstanceOf(ExternalApiException.class)
        .hasMessage("external api failed");

    verify(contentRepository, never()).findAllBySourceInAndTypeInAndExternalIdIn(any(), any(), any());
    verify(contentRepository, never()).saveAll(any());
  }

  @Test
  void syncExternalContents_doesNotSaveWhenAnyClientFetchFails() {
    ExternalContentClient successClient = () -> ExternalContentFetchResult.accepted(
        List.of(candidate("tmdb-1", "New Movie"))
    );
    ExternalContentClient failedClient = () -> {
      throw new ExternalApiException("second external api failed");
    };
    ContentExternalSyncService service = serviceWith(List.of(successClient, failedClient));

    assertThatThrownBy(service::syncExternalContents)
        .isInstanceOf(ExternalApiException.class)
        .hasMessage("second external api failed");

    verify(contentRepository, never()).findAllBySourceInAndTypeInAndExternalIdIn(any(), any(), any());
    verify(contentRepository, never()).saveAll(any());
  }

  private ContentExternalSyncService serviceWith(ExternalContentClient client) {
    return serviceWith(List.of(client));
  }

  private ContentExternalSyncService serviceWith(List<ExternalContentClient> clients) {
    return new ContentExternalSyncService(
        clients,
        contentRepository,
        Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
        new ResourcelessTransactionManager()
    );
  }

  private ExternalContentCandidate candidate(String externalId, String title) {
    return new ExternalContentCandidate(
        ContentType.MOVIE,
        ContentSource.TMDB,
        externalId,
        title,
        "Description",
        "https://image.example.com/" + externalId + ".jpg",
        List.of("movie", "tmdb")
    );
  }
}
