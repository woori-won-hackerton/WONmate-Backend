package wonit.hackathon.a1.domain.consult.dto.response;


import java.time.OffsetDateTime;

public record ConsultRecordResponse(
        Long consultationId,
        ConsultAiRecord record,
        boolean confirmed,
        OffsetDateTime recordConfirmedAt
) {
}