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
import io.mopl.domain.auth.service.TempPasswordService;
import io.mopl.domain.user.dto.data.UserDto;
import io.mopl.domain.user.dto.request.ChangePasswordRequest;
import io.mopl.domain.user.dto.request.UserCreateRequest;
import io.mopl.domain.user.dto.request.UserLockUpdateRequest;
import io.mopl.domain.user.dto.request.UserRoleUpdateRequest;
import io.mopl.domain.user.dto.request.UserUpdateRequest;
import io.mopl.domain.user.entity.Role;
import io.mopl.domain.user.entity.User;
import io.mopl.domain.user.exception.DuplicateUserEmailException;
import io.mopl.domain.user.exception.UserNotFoundException;
import io.mopl.domain.user.facade.UserProfileFacade;
import io.mopl.domain.user.service.UserService;
import io.mopl.global.exception.GlobalExceptionHandler;
import io.mopl.global.response.CursorResponse;
import io.mopl.global.response.SortDirection;
import io.mopl.global.security.MoplUserDetails;
import io.mopl.global.security.MoplUserDetailsService;
import io.mopl.global.security.csrf.CsrfCookieFilter;
import io.mopl.global.security.jwt.JwtProvider;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@WebMvcTest(
    controllers = UserController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, ManagementWebSecurityAutoConfiguration.class}
)
@Import({GlobalExceptionHandler.class, UserControllerTest.MockSecurityConfig.class})
class UserControllerTest {

  @TestConfiguration
  static class MockSecurityConfig implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
      resolvers.add(new HandlerMethodArgumentResolver() {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
          return parameter.getParameterType().isAssignableFrom(MoplUserDetails.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
          User mockUser = User.builder().email("test@example.com").build();
          return new MoplUserDetails(mockUser);
        }
      });
    }

    @Bean
    public CsrfCookieFilter csrfCookieFilter() {
      return new CsrfCookieFilter(null) {
        @Override
        protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            jakarta.servlet.FilterChain filterChain)
            throws jakarta.servlet.ServletException, java.io.IOException {
          filterChain.doFilter(request, response);
        }
      };
    }
  }

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockitoBean
  private UserService userService;


  @MockitoBean
  private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  @MockitoBean
  private JwtProvider jwtProvider;

  @MockitoBean
  private MoplUserDetailsService moplUserDetailsService;

  @MockitoBean
  private StringRedisTemplate stringRedisTemplate;

  @MockitoBean
  private UserProfileFacade userProfileFacade;

  @Test
  @DisplayName("POST /api/users - 회원가입 성공 시 201 반환")
  void createUser_Success() throws Exception {
    UserCreateRequest request = new UserCreateRequest("test@example.com", "password123","홍길동");
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
  @DisplayName("POST /api/users - 이메일 중복 시 409 Conflict 반환")
  void createUser_Fail_DuplicateEmail() throws Exception {
    UserCreateRequest request = new UserCreateRequest("duplicate@example.com","password123", "홍길동");

    given(userService.createUser(any(UserCreateRequest.class)))
        .willThrow(new DuplicateUserEmailException());

    mockMvc.perform(post("/api/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andDo(print())
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("GET /api/users - 유저 다건 커서 페이징 조회 성공 시 200 반환")
  void findUsers_Success() throws Exception {
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
  @DisplayName("GET /api/users - 필수 파라미터(limit) 누락 시 401 반환")
  void findUsers_Fail_MissingParameter() throws Exception {
    mockMvc.perform(get("/api/users")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING"))
        .andDo(print())
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("GET /api/users/{userId} - 단건 조회 성공 시 200 반환")
  void findUser_Success() throws Exception {
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
  @DisplayName("GET /api/users/{userId} - 존재하지 않는 유저 조회 시 404 Not Found 반환")
  void findUser_Fail_NotFound() throws Exception {
    UUID userId = UUID.randomUUID();

    given(userService.findUser(userId)).willThrow(new UserNotFoundException());

    mockMvc.perform(get("/api/users/{userId}", userId))
        .andDo(print())
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PATCH /api/users/{userId} - 프로필 수정 성공 시 200 반환")
  void updateUser_Success() throws Exception {
    UUID userId = UUID.randomUUID();
    UserUpdateRequest request = new UserUpdateRequest("변경된이름");

    MockMultipartFile requestPart = new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)
    );
    MockMultipartFile imagePart = new MockMultipartFile(
        "image", "profile.png", MediaType.IMAGE_PNG_VALUE, "dummy_image_data".getBytes()
    );

    UserDto responseDto = UserDto.builder().id(userId).name("변경된이름").build();

    given(userProfileFacade.updateProfile(eq(userId), any(UserUpdateRequest.class), any())).willReturn(responseDto);

    mockMvc.perform(multipart("/api/users/{userId}", userId)
            .file(requestPart)
            .file(imagePart)
            .with(req -> { req.setMethod("PATCH"); return req; }))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("변경된이름"));
  }

  @Test
  @DisplayName("PATCH /api/users/{userId} - 존재하지 않는 유저 프로필 수정 시 404 반환")
  void updateUser_Fail_NotFound() throws Exception {
    UUID userId = UUID.randomUUID();
    UserUpdateRequest request = new UserUpdateRequest("변경된이름");

    MockMultipartFile requestPart = new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8)
    );

    given(userProfileFacade.updateProfile(eq(userId), any(UserUpdateRequest.class), any()))
        .willThrow(new UserNotFoundException());

    mockMvc.perform(multipart("/api/users/{userId}", userId)
            .file(requestPart)
            .with(req -> { req.setMethod("PATCH"); return req; }))
        .andDo(print())
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("PATCH /api/users/{userId}/role - 권한 변경 성공 시 204 반환")
  void updateUserRole_Success() throws Exception {
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
  @DisplayName("PATCH /api/users/{userId}/password - 비밀번호 변경 성공 시 204 반환")
  void updateUserPassword_Success() throws Exception {
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
  @DisplayName("PATCH /api/users/{userId}/locked - 계정 잠금 설정 성공 시 204 반환")
  void updateUserLocked_Success() throws Exception {
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