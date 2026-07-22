package io.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.content.repository.search.ContentSearchRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentSearchIndexServiceTest {

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private ContentSearchRepository contentSearchRepository;

  private ContentSearchIndexService contentSearchIndexService;

  @BeforeEach
  void setUp() {
    contentSearchIndexService = new ContentSearchIndexService(
        contentRepository,
        contentSearchRepository
    );
  }

  @Test
  void indexesDocumentsLoadedFromDatabase() {
    UUID contentId = UUID.randomUUID();
    Content content = Content.createManual(
        ContentType.MOVIE, "한국 영화", "설명", null, Set.of("드라마")
    );
    ReflectionTestUtils.setField(content, "id", contentId);
    given(contentRepository.findAllByIdWithTags(List.of(contentId))).willReturn(List.of(content));

    contentSearchIndexService.index(contentId);

    verify(contentSearchRepository).saveAll(any());
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
}
