package wonit.hackathon.a1.domain.consult.dto.response;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ConsultAiRecord(

        @NotNull @Size(max = 10000)
        String customerNeeds,

        @NotNull @Size(max = 10000)
        String explainedContent,

        @NotNull @Size(max = 10000)
        String customerQuestions,

        @NotNull @Size(max = 10000)
        String unresolvedIssues,

        @NotNull @Size(max = 10000)
        String nextActions
) {
}