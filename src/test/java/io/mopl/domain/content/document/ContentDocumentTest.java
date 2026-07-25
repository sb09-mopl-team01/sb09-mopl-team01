package io.mopl.domain.content.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ContentDocumentTest {

  @Test
  void createsInitialsAndReviewStatsFromContent() {
    Content content = Content.createManual(
        ContentType.MOVIE,
        "오징어 게임 2: 새로운 시작",
        "한국 영화",
        null,
        Set.of("드라마")
    );
    content.updateReviewStats(4.5, 7);

    ContentDocument document = ContentDocument.from(content);

    assertThat(document.getInitials()).isEqualTo("ㅇㅈㅇㄱㅇㅅㄹㅇㅅㅈ");
    assertThat(document.getReviewCount()).isEqualTo(7);
    assertThat(document.getAverageRating()).isEqualTo(4.5);
  }
}
