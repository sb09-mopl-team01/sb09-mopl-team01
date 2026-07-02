package io.mopl.domain.playlist.mapper;

import io.mopl.domain.content.dto.ContentSummary;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.playlist.dto.PlaylistDto;
import io.mopl.domain.playlist.entity.Playlist;
import io.mopl.domain.playlist.entity.PlaylistContent;
import io.mopl.domain.user.dto.response.UserSummary;
import java.util.HashSet;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import org.springframework.stereotype.Component;

@Component
public class PlaylistMapper {

  public PlaylistDto toDto(Playlist playlist, boolean subscribedByMe) {
    return new PlaylistDto(
        playlist.getId(),
        new UserSummary(
            playlist.getOwner().getId(),
            playlist.getOwner().getName(),
            playlist.getOwner().getProfileImageUrl()
        ),
        playlist.getTitle(),
        playlist.getDescription(),
        playlist.getUpdatedAt() != null ? playlist.getUpdatedAt() : playlist.getCreatedAt(),
        playlist.getSubscriberCount(),
        subscribedByMe,
        playlist.getContents().stream()
            .map(PlaylistContent::getContent)
            .map(this::toContentSummary)
            .collect(Collectors.toList())
    );
  }

  private ContentSummary toContentSummary(Content content) {
    return new ContentSummary(
        content.getId(),
        content.getType(),
        content.getTitle(),
        content.getDescription(),
        content.getThumbnailUrl(),
        new HashSet<>(content.getTags()),
        0.0, // .averageRating(content.getAverageRating()) 추후 추가
        0 // .reviewCount(content.getReviewCount()) 추후 추가
    );
  }
}