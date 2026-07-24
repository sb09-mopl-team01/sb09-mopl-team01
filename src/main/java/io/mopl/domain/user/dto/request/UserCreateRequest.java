package io.mopl.domain.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
    @NotBlank(message = "이메일은 필수 입력값입니다.")
    @Email(message = "올바른 이메일 형식이 아닙니다.")
    String email,
    @NotBlank(message = "비밀번호는 필수 입력 값입니다.")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,20}$",
        message = "비밀번호는 영문자와 숫자를 무조건 포함하여 8~20자리로 입력해주세요."
    )
    String password,
    @NotBlank(message = "이름은 필수 입력값입니다.")
    @Size(min = 2, max = 10, message = "이름은 2자 이상 10자 이하로 입력해주세요.")
    String name
) {}