package io.mopl.domain.watchingsession.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.repository.UserRepository;
import io.mopl.domain.watchingsession.dto.WatchingSessionDto;
import io.mopl.domain.watchingsession.entity.WatchingSession;
import io.mopl.domain.watchingsession.repository.WatchingSessionRepository;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WatchingSessionServiceTest {

  @Autowired
  private WatchingSessionService watchingSessionService;

  @Autowired
  private WatchingSessionRepository watchingSessionRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private ContentRepository contentRepository;

  @Test
  @DisplayName("시청 세션을 생성하고 DTO로 반환한다")
  void startWatching() {
    User watcher = saveUser("고양이");
    Content content = saveContent("인터스텔라");

    WatchingSessionDto result = watchingSessionService.startWatching(watcher.getId(), content.getId());

    assertThat(result.id()).isNotNull();
    assertThat(result.createdAt()).isNotNull();
    assertThat(result.watcher().userId()).isEqualTo(watcher.getId());
    assertThat(result.watcher().name()).isEqualTo("고양이");
    assertThat(result.content().id()).isEqualTo(content.getId());
    assertThat(result.content().title()).isEqualTo("인터스텔라");
  }

  @Test
  @DisplayName("이미 시청 중인 사용자는 새 시청 세션을 생성할 수 없다")
  void startWatchingDuplicatedWatcher() {
    User watcher = saveUser("고양이");
    Content firstContent = saveContent("첫 번째 콘텐츠");
    Content secondContent = saveContent("두 번째 콘텐츠");
    watchingSessionService.startWatching(watcher.getId(), firstContent.getId());

    assertThatThrownBy(() -> watchingSessionService.startWatching(watcher.getId(), secondContent.getId()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WATCHING_SESSION_ALREADY_EXISTS);
  }

  @Test
  @DisplayName("시청 세션을 종료하면 사용자 기준 조회 결과가 null이다")
  void endWatching() {
    User watcher = saveUser("고양이");
    Content content = saveContent("인터스텔라");
    watchingSessionService.startWatching(watcher.getId(), content.getId());

    watchingSessionService.endWatching(watcher.getId());

    assertThat(watchingSessionService.findByWatcher(watcher.getId())).isNull();
    assertThat(watchingSessionRepository.existsByWatcherId(watcher.getId())).isFalse();
  }

  @Test
  @DisplayName("시청 중인 세션이 없으면 종료할 수 없다")
  void endWatchingNotFound() {
    assertThatThrownBy(() -> watchingSessionService.endWatching(UUID.randomUUID()))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.WATCHING_SESSION_NOT_FOUND);
  }

  @Test
  @DisplayName("사용자 기준 현재 시청 세션을 조회한다")
  void findByWatcher() {
    User watcher = saveUser("고양이");
    Content content = saveContent("인터스텔라");
    WatchingSessionDto created = watchingSessionService.startWatching(watcher.getId(), content.getId());

    WatchingSessionDto result = watchingSessionService.findByWatcher(watcher.getId());

    assertThat(result.id()).isEqualTo(created.id());
    assertThat(result.watcher().userId()).isEqualTo(watcher.getId());
    assertThat(result.content().id()).isEqualTo(content.getId());
  }

  @Test
  @DisplayName("콘텐츠 기준 현재 시청 세션 목록을 커서 응답으로 조회한다")
  void findByContent() {
    Content content = saveContent("인터스텔라");
    startSession(saveUser("첫 번째"), content);
    startSession(saveUser("두 번째"), content);
    startSession(saveUser("세 번째"), content);
    startSession(saveUser("다른 콘텐츠 시청자"), saveContent("다른 콘텐츠"));

    CursorResponse<WatchingSessionDto> result = watchingSessionService.findByContent(
        content.getId(),
        null,
        null,
        null,
        2,
        "createdAt",
        SortDirection.DESCENDING
    );

    assertThat(result.data()).hasSize(2);
    assertThat(result.data())
        .extracting(session -> session.content().id())
        .containsOnly(content.getId());
    assertThat(result.hasNext()).isTrue();
    assertThat(result.nextCursor()).isNotBlank();
    assertThat(result.nextIdAfter()).isNotNull();
    assertThat(result.totalCount()).isEqualTo(3L);
  }

  @Test
  @DisplayName("콘텐츠 기준 현재 시청 세션 목록을 시청자 이름으로 필터링한다")
  void findByContentWithWatcherNameLike() {
    Content content = saveContent("인터스텔라");
    startSession(saveUser("고양이"), content);
    startSession(saveUser("강아지"), content);

    CursorResponse<WatchingSessionDto> result = watchingSessionService.findByContent(
        content.getId(),
        "고양",
        null,
        null,
        10,
        "createdAt",
        SortDirection.DESCENDING
    );

    assertThat(result.data())
        .singleElement()
        .extracting(session -> session.watcher().name())
        .isEqualTo("고양이");
    assertThat(result.totalCount()).isEqualTo(1L);
  }

  @Test
  @DisplayName("목록 조회 요청 값이 유효하지 않으면 조회하지 않는다")
  void findByContentWithInvalidCommand() {
    assertThatThrownBy(() -> watchingSessionService.findByContent(
        UUID.randomUUID(),
        null,
        "invalid-cursor",
        UUID.randomUUID(),
        10,
        "createdAt",
        SortDirection.DESCENDING
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_WATCHING_SESSION_CURSOR);

    assertThatThrownBy(() -> watchingSessionService.findByContent(
        UUID.randomUUID(),
        null,
        null,
        null,
        10,
        "watcherName",
        SortDirection.DESCENDING
    ))
        .isInstanceOf(BaseException.class)
        .extracting("errorCode")
        .isEqualTo(ErrorCode.INVALID_WATCHING_SESSION_SORT);
  }

  private WatchingSession startSession(User watcher, Content content) {
    return watchingSessionRepository.saveAndFlush(WatchingSession.start(watcher, content));
  }

  private User saveUser(String name) {
    return userRepository.saveAndFlush(User.builder()
        .email(name + "-" + UUID.randomUUID() + "@example.com")
        .passwordHash("hash")
        .name(name)
        .build());
  }

  private Content saveContent(String title) {
    return contentRepository.saveAndFlush(Content.createManual(
        ContentType.MOVIE,
        title,
        title + " 설명",
        null,
        List.of("영화")
    ));
  }
}
