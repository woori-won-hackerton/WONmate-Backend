package wonit.hackathon.a1.domain.consult.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConsultMemoUpdateRequest(

        @NotNull
        @Size(max = 10000)
        String memo
) {
}