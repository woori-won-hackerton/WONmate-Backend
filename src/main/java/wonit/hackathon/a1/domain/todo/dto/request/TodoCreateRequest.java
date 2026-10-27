package wonit.hackathon.a1.domain.todo.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TodoCreateRequest(

        @NotBlank(message = "할 일 내용은 필수입니다.")
        String content
) {
}