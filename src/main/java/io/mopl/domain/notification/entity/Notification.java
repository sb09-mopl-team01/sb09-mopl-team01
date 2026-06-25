package io.mopl.domain.notification.entity;

import io.mopl.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseEntity {

  @Column(nullable = false)
  private UUID receiverId;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(nullable = false, length = 500)
  private String content;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NotificationLevel level;

  @Column(nullable = false)
  private boolean read;

  private Notification(UUID receiverId, String title, String content, NotificationLevel level) {
    this.receiverId = receiverId;
    this.title = title;
    this.content = content;
    this.level = level;
    this.read = false;
  }

  public static Notification create(
      UUID receiverId,
      String title,
      String content,
      NotificationLevel level
  ) {
    return new Notification(receiverId, title, content, level);
  }
}
