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
import jakarta.persistence.EntityManagerFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
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

  @Autowired
  private EntityManagerFactory entityManagerFactory;

  @AfterEach
  void cleanUp() {
    contentRepository.deleteAll();
  }

  @Test
  void syncExternalContents_commitsEachCandidateAndIsIdempotentOnRerun() {
    ExternalContentClient firstClient = () -> ExternalContentFetchResult.accepted(
        List.of(candidate("tmdb-1", "원본 제목", "원본 설명"))
    );
    ExternalContentClient secondClient = () -> ExternalContentFetchResult.accepted(
        List.of(candidate("tmdb-1", "변경된 제목", "변경된 설명"))
    );

    ExternalContentSyncResult firstResult = serviceWith(firstClient, FIRST_SYNCED_AT)
        .syncExternalContents();
    ExternalContentSyncResult secondResult = serviceWith(secondClient, SECOND_SYNCED_AT)
        .syncExternalContents();

    assertThat(firstResult.createdCount()).isEqualTo(1);
    assertThat(firstResult.skippedCount()).isZero();
    assertThat(secondResult.createdCount()).isZero();
    assertThat(secondResult.skippedCount()).isEqualTo(1);
    assertThat(contentRepository.count()).isEqualTo(1);

    Content storedContent = contentRepository
        .findBySourceAndTypeAndExternalId(ContentSource.TMDB, ContentType.MOVIE, "tmdb-1")
        .orElseThrow();
    assertThat(storedContent.getLastSyncedAt()).isEqualTo(SECOND_SYNCED_AT);
    assertThat(storedContent.getTitle()).isEqualTo("원본 제목");
    assertThat(storedContent.getDescription()).isEqualTo("원본 설명");
  }

  @Test
  void syncExternalContents_keepsCommittedChunkWhenNextChunkFails() {
    List<ExternalContentCandidate> candidates = IntStream
        .rangeClosed(1, ContentExternalSyncService.SYNC_CHUNK_SIZE + 1)
        .mapToObj(index -> candidate("tmdb-" + index))
        .toList();
    ExternalContentClient client = () -> ExternalContentFetchResult.accepted(candidates);
    ContentRepository failOnSecondLookup = failOnSecondLookup(contentRepository);
    ContentExternalSyncService service = new ContentExternalSyncService(
        List.of(client),
        failOnSecondLookup,
        Clock.fixed(FIRST_SYNCED_AT, ZoneOffset.UTC),
        transactionManager
    );

    org.assertj.core.api.Assertions.assertThatThrownBy(service::syncExternalContents)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("second chunk failed");

    assertThat(contentRepository.count()).isEqualTo(ContentExternalSyncService.SYNC_CHUNK_SIZE);
  }

  @Test
  void syncExternalContents_usesOneBulkLookupPerChunk() {
    List<ExternalContentCandidate> candidates = IntStream.rangeClosed(1, 205)
        .mapToObj(index -> candidate("tmdb-" + index))
        .toList();
    ExternalContentClient client = () -> ExternalContentFetchResult.accepted(candidates);
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.clear();

    ExternalContentSyncResult result = serviceWith(client, FIRST_SYNCED_AT)
        .syncExternalContents();

    assertThat(result.createdCount()).isEqualTo(205);
    assertThat(result.skippedCount()).isZero();
    assertThat(statistics.getQueryExecutionCount()).isEqualTo(3);
    assertThat(statistics.getTransactionCount()).isEqualTo(3);
  }

  @Test
  void syncExternalContents_keepsDeletedContentAsTombstone() {
    ExternalContentClient client = () -> ExternalContentFetchResult.accepted(
        List.of(candidate("tmdb-deleted"))
    );
    Content deletedContent = Content.createExternal(
        ContentType.MOVIE,
        "삭제된 외부 영화",
        "삭제된 외부 영화 설명",
        null,
        ContentSource.TMDB,
        "tmdb-deleted",
        FIRST_SYNCED_AT,
        List.of("영화")
    );
    deletedContent.softDelete(Instant.parse("2026-07-15T00:00:00Z"));
    contentRepository.saveAndFlush(deletedContent);

    ExternalContentSyncResult result = serviceWith(client, SECOND_SYNCED_AT)
        .syncExternalContents();

    assertThat(result.createdCount()).isZero();
    assertThat(result.skippedCount()).isEqualTo(1);
    assertThat(contentRepository.count()).isEqualTo(1);
    Content storedContent = contentRepository.findBySourceAndTypeAndExternalId(
        ContentSource.TMDB,
        ContentType.MOVIE,
        "tmdb-deleted"
    ).orElseThrow();
    assertThat(storedContent.isDeleted()).isTrue();
    assertThat(storedContent.getLastSyncedAt()).isEqualTo(FIRST_SYNCED_AT);
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
    return candidate(externalId, "외부 영화", "외부 영화 설명");
  }

  private ExternalContentCandidate candidate(String externalId, String title, String description) {
    return new ExternalContentCandidate(
        ContentType.MOVIE,
        ContentSource.TMDB,
        externalId,
        title,
        description,
        "https://image.example.com/" + externalId + ".jpg",
        List.of("영화")
    );
  }

  private ContentRepository failOnSecondLookup(ContentRepository delegate) {
    AtomicInteger lookupCount = new AtomicInteger();
    return (ContentRepository) Proxy.newProxyInstance(
        ContentRepository.class.getClassLoader(),
        new Class<?>[]{ContentRepository.class},
        (proxy, method, args) -> {
          if (method.getName().equals("findAllBySourceInAndTypeInAndExternalIdIn")
              && lookupCount.incrementAndGet() == 2) {
            throw new IllegalStateException("second chunk failed");
          }
          try {
            return method.invoke(delegate, args);
          } catch (InvocationTargetException exception) {
            throw exception.getCause();
          }
        }
    );
  }
}
