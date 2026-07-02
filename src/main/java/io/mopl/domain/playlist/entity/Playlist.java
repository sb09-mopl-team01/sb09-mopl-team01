package io.mopl.domain.playlist.entity;

import io.mopl.domain.user.entity.User;
import io.mopl.global.entity.BaseUpdatableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "playlists")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Playlist extends BaseUpdatableEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_id", nullable = false)
  private User owner;

  @Column(name = "title", nullable = false, length = 100)
  private String title;

  @Column(name = "description", nullable = false, length = 500)
  private String description;

  @Column(name = "subscriber_count", nullable = false)
  private Long subscriberCount = 0L;

  // 플리에 담긴 콘텐츠 목록, 추가된 순서대로 정렬
  @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("createdAt ASC")
  private List<PlaylistContent> contents = new ArrayList<>();

  private Playlist(User owner, String title, String description) {
    this.owner = owner;
    this.title = title;
    this.description = description;
    this.subscriberCount = 0L;
  }

  public static Playlist create(User owner, String title, String description) {
    return new Playlist(owner, title, description);
  }

  public void update(String title, String description) {
    if (title != null && !title.isBlank()) {
      this.title = title;
    }
    if (description != null && !description.isBlank()) {
      this.description = description;
    }
  }

  public void increaseSubscriberCount() {
    this.subscriberCount++;
  }

  public void decreaseSubscriberCount() {
    if (this.subscriberCount > 0) {
      this.subscriberCount--;
    }
  }
}