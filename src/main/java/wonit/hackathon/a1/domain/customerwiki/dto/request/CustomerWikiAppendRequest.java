package wonit.hackathon.a1.domain.customerwiki.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CustomerWikiAppendRequest(

        @NotBlank(message = "추가할 위키 내용은 필수입니다.")
        String content
) {
}