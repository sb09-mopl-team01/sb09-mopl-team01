package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.content.dto.ExternalContentSyncResult;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.infra.external.ExternalContentCandidate;
import io.mopl.infra.external.ExternalContentClient;
import io.mopl.infra.external.ExternalContentFetchResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import(io.mopl.global.config.AppConfig.class)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ContentExternalSyncServiceIntegrationTest {

  private static final Instant FIRST_SYNCED_AT = Instant.parse("2026-07-10T00:00:00Z");
  private static final Instant SECOND_SYNCED_AT = Instant.parse("2026-07-11T00:00:00Z");

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @AfterEach
  void cleanUp() {
    contentRepository.deleteAll();
  }

  @Test
  void syncExternalContents_commitsEachCandidateAndIsIdempotentOnRerun() {
    ExternalContentClient client = () -> ExternalContentFetchResult.accepted(
        List.of(candidate("tmdb-1"))
    );

    ExternalContentSyncResult firstResult = serviceWith(client, FIRST_SYNCED_AT)
        .syncExternalContents();
    ExternalContentSyncResult secondResult = serviceWith(client, SECOND_SYNCED_AT)
        .syncExternalContents();

    assertThat(firstResult.createdCount()).isEqualTo(1);
    assertThat(firstResult.skippedCount()).isZero();
    assertThat(secondResult.createdCount()).isZero();
    assertThat(secondResult.skippedCount()).isEqualTo(1);
    assertThat(contentRepository.count()).isEqualTo(1);

    Content storedContent = contentRepository
        .findBySourceAndExternalId(ContentSource.TMDB, "tmdb-1")
        .orElseThrow();
    assertThat(storedContent.getLastSyncedAt()).isEqualTo(SECOND_SYNCED_AT);
  }

  private ContentExternalSyncService serviceWith(ExternalContentClient client, Instant syncedAt) {
    return new ContentExternalSyncService(
        List.of(client),
        contentRepository,
        Clock.fixed(syncedAt, ZoneOffset.UTC),
        transactionManager
    );
  }

  private ExternalContentCandidate candidate(String externalId) {
    return new ExternalContentCandidate(
        ContentType.MOVIE,
        ContentSource.TMDB,
        externalId,
        "외부 영화",
        "외부 영화 설명",
        "https://image.example.com/" + externalId + ".jpg",
        List.of("영화")
    );
  }
}
