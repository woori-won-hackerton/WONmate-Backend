package wonit.hackathon.a1.domain.consult.dto.request;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import wonit.hackathon.a1.domain.consult.dto.response.ConsultAiRecord;

public record ConsultRecordUpdateRequest(

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

    public ConsultAiRecord toRecord() {
        return new ConsultAiRecord(
                customerNeeds,
                explainedContent,
                customerQuestions,
                unresolvedIssues,
                nextActions
        );
    }
}