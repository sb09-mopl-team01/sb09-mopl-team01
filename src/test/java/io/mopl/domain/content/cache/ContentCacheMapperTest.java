package io.mopl.domain.content.cache;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.content.cache.dto.ContentBaseCache;
import io.mopl.domain.content.cache.dto.ContentStatsCache;
import io.mopl.domain.content.dto.ContentDto;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ContentCacheMapperTest {

  private final ContentCacheMapper mapper = new ContentCacheMapper();

  @Test
  void mapsEntityToSeparatedCachesAndReassemblesResponse() {
    UUID contentId = UUID.randomUUID();
    Content content = Content.createManual(
        ContentType.MOVIE,
        "title",
        "description",
        "thumbnail",
        Set.of("tag")
    );
    ReflectionTestUtils.setField(content, "id", contentId);
    content.updateReviewStats(4.5, 3);

    ContentBaseCache base = mapper.toBase(content);
    ContentStatsCache stats = mapper.toStats(content);
    ContentDto response = mapper.toDto(new ContentCacheSnapshot(base, stats), 7L);

    assertThat(base.source()).isEqualTo(ContentSource.MANUAL);
    assertThat(base.thumbnailUrl()).isEqualTo("thumbnail");
    assertThat(stats.averageRating()).isEqualTo(4.5);
    assertThat(stats.reviewCount()).isEqualTo(3);
    assertThat(response.id()).isEqualTo(contentId);
    assertThat(response.tags()).containsExactly("tag");
    assertThat(response.watcherCount()).isEqualTo(7L);
  }
}
