package io.mopl.domain.playlist.dto.request;

import jakarta.validation.constraints.Size;

public record PlaylistUpdateRequest(
    @Size(max = 50, message = "제목은 50자 이내로 입력해주세요.")
    String title,

    @Size(max = 200, message = "설명은 200자 이내로 입력해주세요.")
    String description
) {}
