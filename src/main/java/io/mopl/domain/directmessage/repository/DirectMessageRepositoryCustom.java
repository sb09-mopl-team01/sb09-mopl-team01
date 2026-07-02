package io.mopl.domain.directmessage.repository;

import io.mopl.domain.directmessage.entity.DirectMessage;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface DirectMessageRepositoryCustom {

  List<DirectMessage> findByConversationIdWithCursor(
      UUID conversationId,
      Instant cursor,
      UUID idAfter,
      SortDirection sortDirection,
      Pageable pageable
  );

  long countByConversationId(UUID conversationId);
}
