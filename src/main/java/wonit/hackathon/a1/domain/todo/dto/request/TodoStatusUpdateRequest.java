package wonit.hackathon.a1.domain.todo.dto.request;

import jakarta.validation.constraints.NotNull;

public record TodoStatusUpdateRequest(

        @NotNull(message = "완료 여부는 필수입니다.")
        Boolean completed
) {
}