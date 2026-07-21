package io.mopl.domain.content.dto.request;

import io.mopl.domain.content.entity.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record ContentCreateRequest(
    @NotNull(message = "콘텐츠 타입은 필수입니다.")
    ContentType type,

    @NotBlank(message = "콘텐츠 제목은 필수입니다.")
    @Size(max = 255, message = "콘텐츠 제목은 255자를 초과할 수 없습니다.")
    String title,

    @NotBlank(message = "콘텐츠 설명은 필수입니다.")
    @Size(max = 2000, message = "콘텐츠 설명은 2000자를 초과할 수 없습니다.")
    String description,

    @NotEmpty(message = "콘텐츠 태그는 하나 이상 필요합니다.")
    Set<@NotBlank(message = "콘텐츠 태그는 빈 값일 수 없습니다.")
        @Size(max = 50, message = "콘텐츠 태그는 50자를 초과할 수 없습니다.") String> tags
) {
}
