package io.mopl.domain.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.request.ChangePasswordRequest;
import io.mopl.domain.user.dto.request.UserCreateRequest;
import io.mopl.domain.user.dto.request.UserLockUpdateRequest;
import io.mopl.domain.user.dto.request.UserRoleUpdateRequest;
import io.mopl.domain.user.dto.request.UserUpdateRequest;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.service.UserService;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = UserController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class}
)
class UserControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private UserService userService;

  @Test
  @DisplayName("POST /api/users - 회원가입 요청")
  void createUser() throws Exception {
    UserCreateRequest request = new UserCreateRequest("test@example.com", "홍길동", "password123");
    UserDto responseDto = UserDto.builder().email(request.email()).name(request.name()).build();

    given(userService.createUser(any(UserCreateRequest.class))).willReturn(responseDto);

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").value("test@example.com"));
  }

  @Test
  @DisplayName("GET /api/users - 유저 목록 조회")
  void findUsers() throws Exception {
    UserDto userDto = UserDto.builder().email("test@example.com").name("홍길동").build();
    CursorResponse<UserDto> response = new CursorResponse<>(
        List.of(userDto), null, null, false, 1L, "createdAt", SortDirection.DESCENDING
    );

    given(userService.findUsers(
        any(), any(), any(), any(), any(), eq(10), eq("createdAt"), eq(SortDirection.DESCENDING)
    )).willReturn(response);

    mockMvc.perform(get("/api/users")
            .param("limit", "10")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING")
            .param("emailLike", "test"))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].email").value("test@example.com"))
        .andExpect(jsonPath("$.totalCount").value(1));
  }

  @Test
  @DisplayName("GET /api/users/{userId} - 단건 유저 조회")
  void findUser() throws Exception {
    UUID userId = UUID.randomUUID();
    UserDto responseDto = UserDto.builder().id(userId).email("target@example.com").build();

    given(userService.findUser(userId)).willReturn(responseDto);

    mockMvc.perform(get("/api/users/{userId}", userId))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(userId.toString()))
        .andExpect(jsonPath("$.email").value("target@example.com"));
  }

  @Test
  @DisplayName("PATCH /api/users/{userId} - 프로필 수정")
  void updateUser() throws Exception {
    UUID userId = UUID.randomUUID();
    UserUpdateRequest request = new UserUpdateRequest("변경된이름");

    MockMultipartFile requestPart = new MockMultipartFile(
        "request",
        "",
        MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)
    );

    MockMultipartFile imagePart = new MockMultipartFile(
        "image",
        "profile.png",
        MediaType.IMAGE_PNG_VALUE,
        "dummy_image_data".getBytes()
    );

    UserDto responseDto = UserDto.builder().id(userId).name("변경된이름").build();
    given(userService.updateProfile(eq(userId), any(UserUpdateRequest.class), any())).willReturn(responseDto);

    mockMvc.perform(multipart("/api/users/{userId}", userId)
            .file(requestPart)
            .file(imagePart)
            .with(req -> { req.setMethod("PATCH"); return req; }))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("변경된이름"));
  }

  @Test
  @DisplayName("PATCH /api/users/{userId}/role - 권한 변경")
  void updateUserRole() throws Exception {
    UUID userId = UUID.randomUUID();
    UserRoleUpdateRequest request = new UserRoleUpdateRequest(Role.ADMIN);

    mockMvc.perform(patch("/api/users/{userId}/role", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isNoContent());

    verify(userService).updateUserRole(eq(userId), any(UserRoleUpdateRequest.class));
  }

  @Test
  @DisplayName("PATCH /api/users/{userId}/password - 비밀번호 변경")
  void updateUserPassword() throws Exception {
    UUID userId = UUID.randomUUID();
    ChangePasswordRequest request = new ChangePasswordRequest("newPassword123!");

    mockMvc.perform(patch("/api/users/{userId}/password", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isNoContent());

    verify(userService).changePassword(eq(userId), any(ChangePasswordRequest.class));
  }

  @Test
  @DisplayName("PATCH /api/users/{userId}/locked - 계정 잠금 상태 변경")
  void updateUserLocked() throws Exception {
    UUID userId = UUID.randomUUID();
    UserLockUpdateRequest request = new UserLockUpdateRequest(true);

    mockMvc.perform(patch("/api/users/{userId}/locked", userId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isNoContent());

    verify(userService).updateUserLockStatus(eq(userId), any(UserLockUpdateRequest.class));
  }
}
