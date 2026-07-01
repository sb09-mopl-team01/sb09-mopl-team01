package io.mopl.domain.watchingsession.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.watchingsession.entity.WatchingSession;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@Import({
    io.mopl.global.config.AppConfig.class,
    io.mopl.global.config.QueryDslConfig.class
})
@ActiveProfiles("test")
class WatchingSessionRepositoryTest {

  @Autowired
  private WatchingSessionRepository watchingSessionRepository;

  @Autowired
  private TestEntityManager entityManager;

  @Test
  @DisplayName("콘텐츠 기준으로 현재 시청 세션을 최신순 조회한다")
  void findByContentIdWithCursorDesc() {
    Content content = saveContent("인터스텔라");
    Content anotherContent = saveContent("다른 영화");
    WatchingSession first = saveSession(saveUser("고양이"), content);
    WatchingSession second = saveSession(saveUser("강아지"), content);
    saveSession(saveUser("토끼"), anotherContent);

    List<WatchingSession> result = watchingSessionRepository.findByContentIdWithCursorDesc(
        content.getId(),
        null,
        null,
        null,
        PageRequest.of(0, 10)
    );

    assertThat(result)
        .extracting(session -> session.getContent().getId())
        .containsOnly(content.getId());
    assertThat(result)
        .extracting(WatchingSession::getId)
        .containsExactly(second.getId(), first.getId());
  }

  @Test
  @DisplayName("시청자 이름 조건으로 현재 시청 세션을 필터링한다")
  void findByContentIdWithWatcherNameLike() {
    Content content = saveContent("인셉션");
    saveSession(saveUser("고양이"), content);
    saveSession(saveUser("강아지"), content);

    List<WatchingSession> result = watchingSessionRepository.findByContentIdWithCursorDesc(
        content.getId(),
        "고양",
        null,
        null,
        PageRequest.of(0, 10)
    );

    assertThat(result)
        .singleElement()
        .extracting(session -> session.getWatcher().getName())
        .isEqualTo("고양이");
  }

  @Test
  @DisplayName("커서 이후 시청 세션만 오래된순으로 조회한다")
  void findByContentIdWithCursorAscAfterCursor() {
    Content content = saveContent("라라랜드");
    saveSession(saveUser("첫 번째"), content);
    WatchingSession second = saveSession(saveUser("두 번째"), content);
    WatchingSession third = saveSession(saveUser("세 번째"), content);

    List<WatchingSession> result = watchingSessionRepository.findByContentIdWithCursorAsc(
        content.getId(),
        null,
        second.getCreatedAt(),
        second.getId(),
        PageRequest.of(0, 10)
    );

    assertThat(result)
        .extracting(WatchingSession::getId)
        .containsExactly(third.getId());
  }

  @Test
  @DisplayName("콘텐츠 기준 현재 시청 세션 수를 조회한다")
  void countByContentId() {
    Content content = saveContent("매트릭스");
    Content anotherContent = saveContent("다른 콘텐츠");
    saveSession(saveUser("고양이"), content);
    saveSession(saveUser("강아지"), content);
    saveSession(saveUser("토끼"), anotherContent);

    long result = watchingSessionRepository.countByContentId(content.getId(), null);

    assertThat(result).isEqualTo(2L);
  }

  private WatchingSession saveSession(User watcher, Content content) {
    WatchingSession session = WatchingSession.start(watcher, content);
    return watchingSessionRepository.saveAndFlush(session);
  }

  private User saveUser(String name) {
    User user = User.builder()
        .email(name + "@example.com")
        .passwordHash("hash")
        .name(name)
        .build();
    return entityManager.persistFlushFind(user);
  }

  private Content saveContent(String title) {
    Content content = Content.createManual(
        ContentType.MOVIE,
        title,
        title + " 설명",
        null,
        List.of("영화")
    );
    return entityManager.persistFlushFind(content);
  }
}
