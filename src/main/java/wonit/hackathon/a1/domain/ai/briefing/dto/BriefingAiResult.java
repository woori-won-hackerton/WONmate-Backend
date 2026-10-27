package wonit.hackathon.a1.domain.ai.briefing.dto;




import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BriefingAiResult(

        @NotNull
        @Size(min = 1, max = 5)
        List<@NotBlank @Size(max = 200) String> summary,

        @NotNull
        @Size(max = 5)
        List<@NotBlank @Size(max = 200) String> unconfirmedInformation,

        @NotNull
        @Size(min = 1, max = 5)
        List<@NotBlank @Size(max = 200) String> consultationTips
) {
}