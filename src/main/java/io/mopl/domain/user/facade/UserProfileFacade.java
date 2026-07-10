package io.mopl.domain.user.facade;

import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.request.UserUpdateRequest;
import io.mopl.domain.user.service.UserService;
import io.mopl.global.exception.BaseException;
import io.mopl.global.exception.ErrorCode;
import io.mopl.infra.s3.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileFacade {

  private final S3Service s3Service;
  private final UserService userService;

  @Value("${spring.storage.file.upload-dir}")
  private String uploadDir;

  public UserDto updateProfile(UUID userId, UserUpdateRequest request, MultipartFile image) {
    UserDto oldUser = userService.findUser(userId);
    String oldImageUrl = oldUser.profileImageUrl();
    String newImageUrl = oldImageUrl;

    if (image != null && !image.isEmpty()) {
      try {
        newImageUrl = s3Service.uploadFile(image, uploadDir);
      } catch (IOException e) {
        log.error("User Profile Image Upload Failed. userId={}", userId, e);
        throw new BaseException(ErrorCode.PROFILE_IMAGE_UPLOAD_FAIL);
      }
    }

    UserDto updatedUser = userService.updateProfileInfo(userId, request, newImageUrl);

    if (image != null && !image.isEmpty() && oldImageUrl != null) {
      try {
        s3Service.deleteFile(oldImageUrl);
      } catch (Exception e) {
        log.warn("User Previous Profile Image Delete Failed. url={}", oldImageUrl, e);
      }
    }

    return updatedUser;
  }
}
