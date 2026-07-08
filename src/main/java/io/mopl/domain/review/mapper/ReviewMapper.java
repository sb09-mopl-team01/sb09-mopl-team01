package io.mopl.domain.review.mapper;

import io.mopl.domain.review.dto.ReviewDto;
import io.mopl.domain.review.entity.Review;

import io.mopl.domain.user.dto.response.UserSummary;
import io.mopl.domain.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

  @Mapping(source = "content.id", target = "contentId")
  @Mapping(source = "author", target = "author")
  ReviewDto toDto(Review review);

  @Mapping(source = "id", target = "userId")
  @Mapping(source = "name", target = "name")
  @Mapping(source = "profileImageUrl", target = "profileImageUrl")
  UserSummary toUserSummary(User user);
}
