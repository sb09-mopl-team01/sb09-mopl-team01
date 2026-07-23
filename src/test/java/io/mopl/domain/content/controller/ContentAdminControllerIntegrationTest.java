package io.mopl.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.content.dto.request.ContentUpdateRequest;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.ContentRepository;
import io.mopl.domain.content.repository.search.ContentSearchRepository;
import io.mopl.global.config.BaseIntegrationTest;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.storage.type=local",
    "spring.cloud.aws.region.static=ap-northeast-2",
    "mopl.content.thumbnail.storage-type=local",
    "mopl.content.thumbnail.storage-path=build/test-content-thumbnails",
    "mopl.content.thumbnail.url-prefix=/content-thumbnails",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.data.redis.password="
})
class ContentAdminControllerIntegrationTest extends BaseIntegrationTest {

  private static final Path TEST_THUMBNAIL_PATH = Path.of("build/test-content-thumbnails");

  @MockitoBean
  private ContentSearchRepository contentSearchRepository;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private EntityManager entityManager;

  @AfterEach
  void tearDown() throws Exception {
    contentRepository.deleteAll();
    if (Files.exists(TEST_THUMBNAIL_PATH)) {
      try (var paths = Files.walk(TEST_THUMBNAIL_PATH)) {
        paths.sorted(Comparator.reverseOrder())
            .forEach(path -> {
              try {
                Files.deleteIfExists(path);
              } catch (Exception ignored) {
                // 테스트 종료 정리 실패는 기능 검증 결과에 영향을 주지 않는다.
              }
            });
      }
    }
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("관리자는 명세 기준 type 값으로 콘텐츠를 등록, 수정, 삭제할 수 있다")
  void manageContentCrud() throws Exception {
    String createRequest = """
        {
          "type": "movie",
          "title": "등록 제목",
          "description": "등록 설명",
          "tags": ["등록태그", "영화"]
        }
        """;
    MockMultipartFile createRequestPart = jsonPart("request", createRequest);
    MockMultipartFile createThumbnail = imagePart("thumbnail", "create.jpg", MediaType.IMAGE_JPEG_VALUE);

    MvcResult createResult = mockMvc.perform(multipart("/api/contents")
            .file(createRequestPart)
            .file(createThumbnail)
            .with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("movie"))
        .andExpect(jsonPath("$.title").value("등록 제목"))
        .andExpect(jsonPath("$.thumbnailUrl").exists())
        .andReturn();

    UUID contentId = UUID.fromString(
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText()
    );
    transactionTemplate.executeWithoutResult(status -> {
      Content createdContent = contentRepository.findById(contentId).orElseThrow();
      assertThat(createdContent.getSource()).isEqualTo(ContentSource.MANUAL);
      assertThat(createdContent.getType()).isEqualTo(ContentType.MOVIE);
      assertThat(createdContent.getTags()).containsExactlyInAnyOrder("등록태그", "영화");
      assertThat(createdContent.getThumbnailUrl()).startsWith("/content-thumbnails/");
      assertThat(createdContent.getThumbnailKey()).isNotBlank().endsWith(".jpg");
    });

    ContentUpdateRequest updateRequest = new ContentUpdateRequest(
        "수정 제목",
        "수정 설명",
        Set.of("수정태그")
    );
    MockMultipartFile updateRequestPart = jsonPart("request", updateRequest);
    MockMultipartFile updateThumbnail = imagePart("thumbnail", "update.png", MediaType.IMAGE_PNG_VALUE);

    mockMvc.perform(multipart("/api/contents/{contentId}", contentId)
            .file(updateRequestPart)
            .file(updateThumbnail)
            .with(request -> {
              request.setMethod("PATCH");
              return request;
            })
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("수정 제목"))
        .andExpect(jsonPath("$.description").value("수정 설명"));

    transactionTemplate.executeWithoutResult(status -> {
      Content updatedContent = contentRepository.findById(contentId).orElseThrow();
      assertThat(updatedContent.getTitle()).isEqualTo("수정 제목");
      assertThat(updatedContent.getDescription()).isEqualTo("수정 설명");
      assertThat(updatedContent.getTags()).containsExactly("수정태그");
      assertThat(updatedContent.getThumbnailUrl()).endsWith(".png");
      assertThat(updatedContent.getThumbnailKey()).isNotBlank().endsWith(".png");
    });

    mockMvc.perform(delete("/api/contents/{contentId}", contentId)
            .with(csrf()))
        .andExpect(status().isOk());

    assertThat(contentRepository.findById(contentId)).isEmpty();
    transactionTemplate.executeWithoutResult(status -> {
      Content deletedContent = entityManager.find(Content.class, contentId);
      assertThat(deletedContent).isNotNull();
      assertThat(deletedContent.getDeletedAt()).isNotNull();
      assertThat(deletedContent.getThumbnailKey()).isNotBlank().endsWith(".png");
    });
  }

  @Test
  @WithMockUser
  @DisplayName("콘텐츠 조회 API는 Swagger 조회 계약에 맞는 응답을 반환한다")
  void findContentContract() throws Exception {
    Content content = contentRepository.saveAndFlush(Content.createManual(
        ContentType.MOVIE,
        "조회 제목",
        "조회 설명",
        null,
        Set.of("영화")
    ));

    mockMvc.perform(get("/api/contents/{contentId}", content.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(content.getId().toString()))
        .andExpect(jsonPath("$.type").value("movie"))
        .andExpect(jsonPath("$.title").value("조회 제목"))
        .andExpect(jsonPath("$.averageRating").value(0.0))
        .andExpect(jsonPath("$.reviewCount").value(0))
        .andExpect(jsonPath("$.watcherCount").value(0));

    mockMvc.perform(get("/api/contents")
            .param("typeEqual", "movie")
            .param("keywordLike", "조회")
            .param("tagsIn", "영화")
            .param("limit", "10")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(content.getId().toString()))
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.sortBy").value("createdAt"))
        .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));
  }

  @Test
  @WithMockUser
  @DisplayName("콘텐츠 조회 API는 잘못된 조회 조건에 400을 반환한다")
  void rejectInvalidContentQueryContract() throws Exception {
    mockMvc.perform(get("/api/contents")
            .param("typeEqual", "invalid")
            .param("limit", "10")
            .param("sortBy", "createdAt")
            .param("sortDirection", "DESCENDING"))
        .andExpect(status().isBadRequest());
  }

  private MockMultipartFile jsonPart(String name, Object value) throws Exception {
    byte[] content = value instanceof String text
        ? text.getBytes(StandardCharsets.UTF_8)
        : objectMapper.writeValueAsBytes(value);

    return new MockMultipartFile(
        name,
        "",
        MediaType.APPLICATION_JSON_VALUE,
        content
    );
  }

  private MockMultipartFile imagePart(String name, String filename, String contentType) {
    return new MockMultipartFile(
        name,
        filename,
        contentType,
        "thumbnail".getBytes(StandardCharsets.UTF_8)
    );
  }
}
