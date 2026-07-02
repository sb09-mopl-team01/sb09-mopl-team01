package io.mopl.domain.watchingsession.entity;

import io.mopl.domain.content.entity.Content;
import io.mopl.domain.user.entity.User;
import io.mopl.global.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "watching_sessions",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_watching_sessions_watcher_id", columnNames = "watcher_id")
    },
    indexes = {
        @Index(name = "idx_watching_sessions_content_created_at_id", columnList = "content_id, created_at, id")
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchingSession extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "watcher_id", nullable = false)
  private User watcher;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "content_id", nullable = false)
  private Content content;

  private WatchingSession(User watcher, Content content) {
    this.watcher = Objects.requireNonNull(watcher, "시청자는 필수입니다.");
    this.content = Objects.requireNonNull(content, "시청 콘텐츠는 필수입니다.");
  }

  public static WatchingSession start(User watcher, Content content) {
    return new WatchingSession(watcher, content);
  }
}
