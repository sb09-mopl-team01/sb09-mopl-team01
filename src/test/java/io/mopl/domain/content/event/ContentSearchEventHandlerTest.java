package io.mopl.domain.content.event;

import static org.mockito.Mockito.verify;

import io.mopl.domain.content.service.ContentSearchIndexService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentSearchEventHandlerTest {

  @Mock
  private ContentSearchIndexService contentSearchIndexService;

  @InjectMocks
  private ContentSearchEventHandler contentSearchEventHandler;

  @Test
  void reindexesContentWhenReviewStatsChange() {
    UUID contentId = UUID.randomUUID();

    contentSearchEventHandler.handleReviewStatsChanged(new ReviewStatsChangedEvent(contentId));

    verify(contentSearchIndexService).index(contentId);
  }
}
