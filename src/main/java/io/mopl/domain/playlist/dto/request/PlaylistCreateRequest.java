package io.mopl.domain.playlist.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PlaylistCreateRequest(
    @NotBlank(message = "플레이리스트 제목은 필수입니다.")
    String title,

    @NotBlank(message = "플레이리스트 설명은 필수입니다.")
    String description
) {}
