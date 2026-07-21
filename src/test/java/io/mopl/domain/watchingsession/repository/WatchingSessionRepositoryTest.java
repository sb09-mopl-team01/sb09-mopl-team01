package io.mopl.domain.watchingsession.repository;

import static org.assertj.core.api.Assertions.assertThat;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.watchingsession.entity.WatchingSession;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    List<WatchingSession> sessions = List.of(
        saveSession(saveUser("첫 번째"), content),
        saveSession(saveUser("두 번째"), content),
        saveSession(saveUser("세 번째"), content)
    ).stream()
        .sorted(Comparator
            .comparing((WatchingSession session) -> session.getCreatedAt()
                .truncatedTo(ChronoUnit.MICROS))
            .thenComparing(WatchingSession::getId))
        .toList();
    WatchingSession cursor = sessions.get(1);

    List<WatchingSession> result = watchingSessionRepository.findByContentIdWithCursorAsc(
        content.getId(),
        null,
        cursor.getCreatedAt(),
        cursor.getId(),
        PageRequest.of(0, 10)
    );

    assertThat(result)
        .extracting(WatchingSession::getId)
        .containsExactly(sessions.get(2).getId());
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

  @Test
  @DisplayName("콘텐츠 목록 기준 현재 시청 세션 수를 한 번에 조회한다")
  void countByContentIds() {
    Content firstContent = saveContent("첫 번째 콘텐츠");
    Content secondContent = saveContent("두 번째 콘텐츠");
    Content emptyContent = saveContent("시청자 없는 콘텐츠");
    saveSession(saveUser("첫 번째 시청자"), firstContent);
    saveSession(saveUser("두 번째 시청자"), firstContent);
    saveSession(saveUser("세 번째 시청자"), secondContent);

    Map<UUID, Long> result = watchingSessionRepository.countByContentIds(List.of(
        firstContent.getId(),
        secondContent.getId(),
        emptyContent.getId()
    ));

    assertThat(result)
        .containsEntry(firstContent.getId(), 2L)
        .containsEntry(secondContent.getId(), 1L)
        .doesNotContainKey(emptyContent.getId());
  }

  @Test
  @DisplayName("사용자가 특정 콘텐츠를 시청 중인지 확인한다")
  void existsByWatcherIdAndContentId() {
    Content content = saveContent("그래비티");
    Content anotherContent = saveContent("다른 콘텐츠");
    User watcher = saveUser("시청자");
    saveSession(watcher, content);

    assertThat(watchingSessionRepository.existsByWatcherIdAndContentId(
        watcher.getId(),
        content.getId()
    )).isTrue();
    assertThat(watchingSessionRepository.existsByWatcherIdAndContentId(
        watcher.getId(),
        anotherContent.getId()
    )).isFalse();
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
