package io.mopl.domain.watchingsession.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.domain.watchingsession.entity.WatchingSession;
import io.mopl.domain.watchingsession.mapper.WatchingSessionMapper;
import io.mopl.domain.watchingsession.repository.WatchingSessionRepository;
import io.mopl.global.event.DomainEventPublisher;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class WatchingSessionServiceUnitTest {

  @InjectMocks
  private WatchingSessionService watchingSessionService;

  @Mock
  private WatchingSessionRepository watchingSessionRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private ContentRepository contentRepository;

  @Mock
  private WatchingSessionMapper watchingSessionMapper;

  @Mock
  private DomainEventPublisher domainEventPublisher;

  @Test
  void startWatchingConvertsUniqueConstraintViolationToDomainException() {
    UUID watcherId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    User watcher = User.builder()
        .email("watcher@example.com")
        .passwordHash("hash")
        .name("watcher")
        .build();
    Content content = Content.createManual(
        ContentType.MOVIE,
        "콘텐츠",
        "콘텐츠 설명",
        null,
        List.of("영화")
    );
    ReflectionTestUtils.setField(watcher, "id", watcherId);
    ReflectionTestUtils.setField(content, "id", contentId);

    when(watchingSessionRepository.existsByWatcherId(watcherId)).thenReturn(false);
    when(userRepository.findById(watcherId)).thenReturn(Optional.of(watcher));
    when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
    when(watchingSessionRepository.saveAndFlush(any(WatchingSession.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate watcher"));

    assertThatThrownBy(() -> watchingSessionService.startWatching(watcherId, contentId))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WATCHING_SESSION_ALREADY_EXISTS);
  }
}
