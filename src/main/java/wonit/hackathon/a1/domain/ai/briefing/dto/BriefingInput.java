package wonit.hackathon.a1.domain.ai.briefing.dto;



import tools.jackson.databind.JsonNode;
import wonit.hackathon.a1.domain.consult.entity.Consult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

public record BriefingInput(
        String customerWiki,
        List<ConsultationHistory> histories
) {

    public BriefingInput {
        customerWiki = customerWiki == null ? "" : customerWiki;
        histories = List.copyOf(
                Objects.requireNonNull(histories, "histories")
        );
    }

    public record ConsultationHistory(
            Long consultationId,
            OffsetDateTime scheduledAt,
            String consultType,
            String purpose,
            String memo,
            JsonNode aiRecord
    ) {

        public static ConsultationHistory from(Consult consult) {
            return new ConsultationHistory(
                    consult.getId(),
                    consult.getScheduledAt(),
                    consult.getConsultType() == null
                            ? null
                            : consult.getConsultType().name(),
                    consult.getPurpose(),
                    consult.getMemo(),
                    consult.getAiRecord()
            );
        }
    }
}