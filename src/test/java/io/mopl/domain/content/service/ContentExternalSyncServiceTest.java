package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.dto.ExternalContentSyncResult;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.infra.external.ExternalApiException;
import io.mopl.infra.external.ExternalContentCandidate;
import io.mopl.infra.external.ExternalContentClient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
    ExternalContentClient client = () -> List.of(
        candidate("tmdb-1", "New Movie"),
        candidate("tmdb-2", "Existing Movie")
    );
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

    given(contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "tmdb-1"))
        .willReturn(Optional.empty());
    given(contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "tmdb-2"))
        .willReturn(Optional.of(existingContent));
    given(contentRepository.save(any(Content.class))).willAnswer(invocation -> invocation.getArgument(0));

    ExternalContentSyncResult result = service.syncExternalContents();

    assertThat(result.createdCount()).isEqualTo(1);
    assertThat(result.skippedCount()).isEqualTo(1);
    assertThat(result.failedCount()).isZero();
    assertThat(result.syncedAt()).isEqualTo(FIXED_NOW);
    assertThat(existingContent.getLastSyncedAt()).isEqualTo(FIXED_NOW);

    ArgumentCaptor<Content> contentCaptor = ArgumentCaptor.forClass(Content.class);
    verify(contentRepository).save(contentCaptor.capture());
    Content savedContent = contentCaptor.getValue();
    assertThat(savedContent.getSource()).isEqualTo(ContentSource.TMDB);
    assertThat(savedContent.getExternalId()).isEqualTo("tmdb-1");
    assertThat(savedContent.getLastSyncedAt()).isEqualTo(FIXED_NOW);
  }

  @Test
  void syncExternalContents_skipsInvalidItemAndContinues() {
    ExternalContentClient client = () -> List.of(
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
    );
    ContentExternalSyncService service = serviceWith(client);

    given(contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "tmdb-1"))
        .willReturn(Optional.empty());
    given(contentRepository.save(any(Content.class))).willAnswer(invocation -> invocation.getArgument(0));

    ExternalContentSyncResult result = service.syncExternalContents();

    assertThat(result.createdCount()).isEqualTo(1);
    assertThat(result.skippedCount()).isZero();
    assertThat(result.failedCount()).isEqualTo(1);
    verify(contentRepository).save(any(Content.class));
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

    verify(contentRepository, never()).findBySourceAndExternalId(any(), any());
    verify(contentRepository, never()).save(any());
  }

  @Test
  void syncExternalContents_doesNotSaveWhenAnyClientFetchFails() {
    ExternalContentClient successClient = () -> List.of(candidate("tmdb-1", "New Movie"));
    ExternalContentClient failedClient = () -> {
      throw new ExternalApiException("second external api failed");
    };
    ContentExternalSyncService service = serviceWith(List.of(successClient, failedClient));

    assertThatThrownBy(service::syncExternalContents)
        .isInstanceOf(ExternalApiException.class)
        .hasMessage("second external api failed");

    verify(contentRepository, never()).findBySourceAndExternalId(any(), any());
    verify(contentRepository, never()).save(any());
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
