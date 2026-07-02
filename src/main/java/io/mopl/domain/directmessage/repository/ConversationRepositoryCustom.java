package io.mopl.domain.directmessage.repository;

import io.mopl.domain.directmessage.entity.Conversation;
import io.mopl.global.response.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface ConversationRepositoryCustom {

  List<Conversation> findMyConversationsWithCursor(
      UUID requesterId,
      String keywordLike,
      Instant cursor,
      UUID idAfter,
      SortDirection sortDirection,
      Pageable pageable
  );

  long countMyConversations(UUID requesterId, String keywordLike);
}
