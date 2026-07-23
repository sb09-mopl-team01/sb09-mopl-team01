package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.dto.ContentThumbnailCleanupCandidate;
import io.mopl.domain.content.dto.ContentThumbnailCleanupResult;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.ContentRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentThumbnailCleanupServiceTest {

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ContentThumbnailService contentThumbnailService;

  @Test
  void cleanupProcessesPagesBackwardsAndKeepsFailedItemsForRetry() {
    Instant cutoff = Instant.parse("2026-04-23T00:00:00Z");
    Content cleaned = deletedContent("cleaned.jpg", cutoff.minusSeconds(3));
    Content failed = deletedContent("failed.jpg", cutoff.minusSeconds(2));
    UUID staleId = UUID.randomUUID();
    ContentThumbnailCleanupCandidate cleanedCandidate = candidate(cleaned);
    ContentThumbnailCleanupCandidate failedCandidate = candidate(failed);
    ContentThumbnailCleanupCandidate staleCandidate = new ContentThumbnailCleanupCandidate(
        staleId,
        "stale.jpg",
        cutoff.minusSeconds(1)
    );
    PageRequest firstPageRequest = PageRequest.of(0, 2);
    PageRequest lastPageRequest = PageRequest.of(1, 2);
    given(contentRepository.findThumbnailCleanupCandidates(cutoff, firstPageRequest))
        .willReturn(new PageImpl<>(
            List.of(cleanedCandidate, failedCandidate),
            firstPageRequest,
            3
        ));
    given(contentRepository.findThumbnailCleanupCandidates(cutoff, lastPageRequest))
        .willReturn(new PageImpl<>(List.of(staleCandidate), lastPageRequest, 3));
    given(contentRepository.findByIdIncludingDeleted(cleaned.getId()))
        .willReturn(Optional.of(cleaned));
    given(contentRepository.findByIdIncludingDeleted(staleId)).willReturn(Optional.empty());
    doAnswer(invocation -> {
      if ("failed.jpg".equals(invocation.getArgument(0))) {
        throw new IllegalStateException("storage unavailable");
      }
      return null;
    }).when(contentThumbnailService).deleteOrThrow(org.mockito.ArgumentMatchers.anyString());
    ContentThumbnailCleanupService service = service();

    ContentThumbnailCleanupResult result = service.cleanupExpiredThumbnails(cutoff, 2);

    assertThat(result).isEqualTo(new ContentThumbnailCleanupResult(3, 1, 1, 1));
    assertThat(cleaned.getThumbnailUrl()).isNull();
    assertThat(cleaned.getThumbnailKey()).isNull();
    assertThat(failed.getThumbnailKey()).isEqualTo("failed.jpg");
    verify(contentThumbnailService).deleteOrThrow("cleaned.jpg");
    verify(contentThumbnailService).deleteOrThrow("failed.jpg");
    verify(contentThumbnailService).deleteOrThrow("stale.jpg");
  }

  @Test
  void cleanupRejectsInvalidChunkSize() {
    assertThatThrownBy(
        () -> service().cleanupExpiredThumbnails(Instant.parse("2026-04-23T00:00:00Z"), 0)
    ).isInstanceOf(IllegalArgumentException.class);
  }

  private ContentThumbnailCleanupService service() {
    return new ContentThumbnailCleanupService(
        contentRepository,
        contentThumbnailService,
        new ResourcelessTransactionManager()
    );
  }

  private Content deletedContent(String thumbnailKey, Instant deletedAt) {
    Content content = Content.createManual(
        ContentType.MOVIE,
        thumbnailKey,
        "description",
        "/content-thumbnails/" + thumbnailKey,
        thumbnailKey,
        Set.of("영화")
    );
    ReflectionTestUtils.setField(content, "id", UUID.randomUUID());
    content.softDelete(deletedAt);
    return content;
  }

  private ContentThumbnailCleanupCandidate candidate(Content content) {
    return new ContentThumbnailCleanupCandidate(
        content.getId(),
        content.getThumbnailKey(),
        content.getDeletedAt()
    );
  }
}
