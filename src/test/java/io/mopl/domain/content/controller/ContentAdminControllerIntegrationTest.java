package io.mopl.domain.content.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mopl.domain.content.dto.request.ContentCreateRequest;
import io.mopl.domain.content.dto.request.ContentUpdateRequest;
import io.mopl.domain.content.entity.Content;
import io.mopl.domain.content.entity.ContentSource;
import io.mopl.domain.content.entity.ContentType;
import io.mopl.domain.content.repository.ContentRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "mopl.content.thumbnail.storage-path=build/test-content-thumbnails",
    "mopl.content.thumbnail.url-prefix=/content-thumbnails",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "spring.data.redis.password="
})
class ContentAdminControllerIntegrationTest {

  private static final Path TEST_THUMBNAIL_PATH = Path.of("build/test-content-thumbnails");

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ContentRepository contentRepository;

  @Autowired
  private TransactionTemplate transactionTemplate;

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
  @DisplayName("관리자는 콘텐츠를 등록, 수정, 삭제할 수 있다")
  void manageContentCrud() throws Exception {
    ContentCreateRequest createRequest = new ContentCreateRequest(
        ContentType.MOVIE,
        "등록 제목",
        "등록 설명",
        Set.of("등록태그", "영화")
    );
    MockMultipartFile createRequestPart = jsonPart("request", createRequest);
    MockMultipartFile createThumbnail = imagePart("thumbnail", "create.jpg");

    MvcResult createResult = mockMvc.perform(multipart("/api/contents")
            .file(createRequestPart)
            .file(createThumbnail)
            .with(csrf()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value("등록 제목"))
        .andExpect(jsonPath("$.thumbnailUrl").exists())
        .andReturn();

    UUID contentId = UUID.fromString(
        objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText()
    );
    transactionTemplate.executeWithoutResult(status -> {
      Content createdContent = contentRepository.findById(contentId).orElseThrow();
      assertThat(createdContent.getSource()).isEqualTo(ContentSource.MANUAL);
      assertThat(createdContent.getTags()).containsExactlyInAnyOrder("등록태그", "영화");
      assertThat(createdContent.getThumbnailUrl()).startsWith("/content-thumbnails/");
    });

    ContentUpdateRequest updateRequest = new ContentUpdateRequest(
        "수정 제목",
        "수정 설명",
        Set.of("수정태그")
    );
    MockMultipartFile updateRequestPart = jsonPart("request", updateRequest);
    MockMultipartFile updateThumbnail = imagePart("thumbnail", "update.png");

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
    });

    mockMvc.perform(delete("/api/contents/{contentId}", contentId)
            .with(csrf()))
        .andExpect(status().isOk());

    assertThat(contentRepository.existsById(contentId)).isFalse();
  }

  private MockMultipartFile jsonPart(String name, Object value) throws Exception {
    return new MockMultipartFile(
        name,
        "",
        MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsBytes(value)
    );
  }

  private MockMultipartFile imagePart(String name, String filename) {
    return new MockMultipartFile(
        name,
        filename,
        MediaType.IMAGE_JPEG_VALUE,
        "thumbnail".getBytes()
    );
  }
}
