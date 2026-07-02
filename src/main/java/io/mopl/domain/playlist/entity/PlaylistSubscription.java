package io.mopl.domain.playlist.entity;

import io.mopl.domain.user.entity.User;

import io.mopl.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "playlist_subscriptions",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_playlist_subscription_user",
            columnNames = {"playlist_id", "user_id"}
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaylistSubscription extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "playlist_id", nullable = false)
  private Playlist playlist;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  public PlaylistSubscription(Playlist playlist, User user) {
    this.playlist = playlist;
    this.user = user;
  }

  public static PlaylistSubscription create(Playlist playlist, User user) {
    return new PlaylistSubscription(playlist, user);
  }
}