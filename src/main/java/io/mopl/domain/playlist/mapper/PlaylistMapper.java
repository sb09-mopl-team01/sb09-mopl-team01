package io.mopl.domain.playlist.mapper;

import io.mopl.domain.content.dto.ContentSummary;
import io.mopl.domain.playlist.dto.PlaylistDto;
import io.mopl.domain.playlist.entity.Playlist;
import io.mopl.domain.playlist.entity.PlaylistContent;
import io.mopl.domain.user.dto.response.UserSummary;
import io.mopl.domain.user.entity.User;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlaylistMapper {

  public PlaylistDto toDto(Playlist playlist, boolean subscribedByMe) {
    User owner = playlist.getOwner();
    UserSummary ownerSummary = new UserSummary(
        owner.getId(),
        owner.getName(),
        owner.getProfileImageUrl()
    );

    List<ContentSummary> contentSummaries = playlist.getContents().stream()
        .map(PlaylistContent::getContent)
        .map(c -> new ContentSummary(
            c.getId(),
            c.getType(),
            c.getTitle(),
            c.getDescription(),
            c.getThumbnailUrl(),
            c.getTags(),
            0.0, // Content에서 getAverageRating() 생성 후 연동 필요
            0    // Content에서 getReviewCount() 생성 후 연동 필요
        )).toList();

    return new PlaylistDto(
        playlist.getId(),
        ownerSummary,
        playlist.getTitle(),
        playlist.getDescription(),
        playlist.getUpdatedAt(),
        playlist.getSubscriberCount(),
        subscribedByMe,
        contentSummaries
    );
  }
}