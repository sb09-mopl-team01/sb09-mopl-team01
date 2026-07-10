package io.mopl.domain.user.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.request.UserUpdateRequest;
import io.mopl.domain.user.service.UserService;
import io.mopl.infra.s3.S3Service;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class UserProfileFacadeTest {

  @InjectMocks
  private UserProfileFacade userProfileFacade;

  @Mock
  private S3Service s3Service;

  @Mock
  private UserService userService;

  private final String UPLOAD_DIR = "profiles";

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(userProfileFacade, "uploadDir", UPLOAD_DIR);
  }

  @Test
  @DisplayName("새로운 이미지가 있을 때 S3 업로드, DB 업데이트, 기존 이미지 삭제가 정상적으로 호출된다")
  void updateProfile_WithNewImage_Success() throws IOException {
    UUID userId = UUID.randomUUID();
    UserUpdateRequest request = new UserUpdateRequest("변경된이름");
    MockMultipartFile image = new MockMultipartFile("image", "new.png", "image/png", "data".getBytes());

    String oldImageUrl = "https://s3.com/old-image.jpg";
    String newImageUrl = "https://s3.com/new-image.jpg";

    UserDto oldUserDto = UserDto.builder().id(userId).profileImageUrl(oldImageUrl).build();
    UserDto updatedUserDto = UserDto.builder().id(userId).name("변경된이름").profileImageUrl(newImageUrl).build();

    given(userService.findUser(userId)).willReturn(oldUserDto);
    given(s3Service.uploadFile(image, UPLOAD_DIR)).willReturn(newImageUrl);
    given(userService.updateProfileInfo(userId, request, newImageUrl)).willReturn(updatedUserDto);

    UserDto result = userProfileFacade.updateProfile(userId, request, image);

    assertThat(result.name()).isEqualTo("변경된이름");
    assertThat(result.profileImageUrl()).isEqualTo(newImageUrl);

    verify(s3Service).uploadFile(image, UPLOAD_DIR);
    verify(userService).updateProfileInfo(userId, request, newImageUrl);
    verify(s3Service).deleteFile(oldImageUrl);
  }

  @Test
  @DisplayName("새로운 이미지가 없을 때 S3 통신 없이 DB 업데이트만 정상적으로 호출된다")
  void updateProfile_WithoutNewImage_Success() throws IOException {
    UUID userId = UUID.randomUUID();
    UserUpdateRequest request = new UserUpdateRequest("변경된이름");
    MultipartFile image = null;

    String oldImageUrl = "https://s3.com/old-image.jpg";

    UserDto oldUserDto = UserDto.builder().id(userId).profileImageUrl(oldImageUrl).build();
    UserDto updatedUserDto = UserDto.builder().id(userId).name("변경된이름").profileImageUrl(oldImageUrl).build();

    given(userService.findUser(userId)).willReturn(oldUserDto);
    given(userService.updateProfileInfo(userId, request, oldImageUrl)).willReturn(updatedUserDto);

    UserDto result = userProfileFacade.updateProfile(userId, request, image);

    verify(s3Service, never()).uploadFile(any(), anyString());
    verify(s3Service, never()).deleteFile(anyString());
    verify(userService).updateProfileInfo(userId, request, oldImageUrl);
  }

  @Test
  @DisplayName("S3 업로드 실패 시 RuntimeException을 던진다")
  void updateProfile_Fail_WhenS3UploadThrowsException() throws IOException {
    UUID userId = UUID.randomUUID();
    UserUpdateRequest request = new UserUpdateRequest("변경된이름");
    MockMultipartFile image = new MockMultipartFile("image", "new.png", "image/png", "data".getBytes());

    UserDto oldUserDto = UserDto.builder().id(userId).profileImageUrl(null).build();

    given(userService.findUser(userId)).willReturn(oldUserDto);
    given(s3Service.uploadFile(image, UPLOAD_DIR)).willThrow(new IOException("S3 Upload Error"));

    RuntimeException exception = assertThrows(RuntimeException.class,
        () -> userProfileFacade.updateProfile(userId, request, image));

    assertThat(exception.getMessage()).isEqualTo("프로필 이미지 업로드 중 오류가 발생했습니다.");
    verify(userService, never()).updateProfileInfo(any(), any(), any());
  }
}
