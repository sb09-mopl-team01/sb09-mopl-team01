package io.mopl.domain.playlist.entity;

import io.mopl.domain.content.entity.Content;
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
    name = "playlist_contents",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_playlist_content",
            columnNames = {"playlist_id", "content_id"}
        )
    }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaylistContent extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "playlist_id", nullable = false)
  private Playlist playlist;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "content_id", nullable = false)
  private Content content;

  private PlaylistContent(Playlist playlist, Content content) {
    this.playlist = playlist;
    this.content = content;
  }

  public static PlaylistContent create(Playlist playlist, Content content) {
    return new PlaylistContent(playlist, content);
  }
}