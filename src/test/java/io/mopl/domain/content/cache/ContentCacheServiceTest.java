package io.mopl.domain.content.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.BDDMockito.given;

import io.mopl.domain.content.cache.dto.ContentBaseCache;
import io.mopl.domain.content.cache.dto.ContentStatsCache;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentCacheServiceTest {

  @Mock
  private ContentCacheRepository cacheRepository;

  @Mock
  private ContentCacheMapper cacheMapper;

  @Mock
  private Content content;

  @Test
  void resolveMissingStoresOnlyMissingBaseCache() {
    UUID contentId = UUID.randomUUID();
    ContentStatsCache cachedStats = new ContentStatsCache(contentId, 4.0, 2);
    ContentBaseCache newBase = new ContentBaseCache(
        contentId,
        "title",
        "description",
        ContentType.MOVIE,
        ContentSource.MANUAL,
        null,
        Set.of("tag")
    );
    ContentCacheService service = new ContentCacheService(cacheRepository, cacheMapper);
    given(cacheMapper.toBase(content)).willReturn(newBase);

    ContentCacheSnapshot result = service.resolveMissing(
        content,
        new ContentCacheSnapshot(null, cachedStats)
    );

    assertThat(result.base()).isEqualTo(newBase);
    assertThat(result.stats()).isEqualTo(cachedStats);
    verify(cacheRepository).putBases(List.of(newBase));
    verify(cacheRepository, never()).putStats(org.mockito.ArgumentMatchers.anyCollection());
  }

  @Test
  void resolveMissingDoesNotWriteCompleteCacheAgain() {
    UUID contentId = UUID.randomUUID();
    ContentBaseCache base = new ContentBaseCache(
        contentId,
        "title",
        "description",
        ContentType.MOVIE,
        ContentSource.MANUAL,
        null,
        Set.of("tag")
    );
    ContentStatsCache stats = new ContentStatsCache(contentId, 4.0, 2);
    ContentCacheSnapshot complete = new ContentCacheSnapshot(base, stats);
    ContentCacheService service = new ContentCacheService(cacheRepository, cacheMapper);

    ContentCacheSnapshot result = service.resolveMissing(content, complete);

    assertThat(result).isEqualTo(complete);
    verify(cacheRepository, never()).putBases(org.mockito.ArgumentMatchers.anyCollection());
    verify(cacheRepository, never()).putStats(org.mockito.ArgumentMatchers.anyCollection());
  }

  @Test
  void resolveMissingContentsWritesMissingCachesInBatches() {
    UUID contentId = UUID.randomUUID();
    ContentBaseCache base = new ContentBaseCache(
        contentId,
        "title",
        "description",
        ContentType.MOVIE,
        ContentSource.MANUAL,
        null,
        Set.of("tag")
    );
    ContentStatsCache stats = new ContentStatsCache(contentId, 4.0, 2);
    ContentCacheService service = new ContentCacheService(cacheRepository, cacheMapper);
    given(content.getId()).willReturn(contentId);
    given(cacheMapper.toBase(content)).willReturn(base);
    given(cacheMapper.toStats(content)).willReturn(stats);

    Map<UUID, ContentCacheSnapshot> result = service.resolveMissing(
        List.of(content),
        Map.of(contentId, ContentCacheSnapshot.empty())
    );

    assertThat(result.get(contentId)).isEqualTo(new ContentCacheSnapshot(base, stats));
    verify(cacheRepository).putBases(List.of(base));
    verify(cacheRepository).putStats(List.of(stats));
  }
}
