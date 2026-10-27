package wonit.hackathon.a1.domain.consult.dto.response;


import java.time.LocalDateTime;

public record ConsultMemoResponse(
        Long consultationId,
        String memo,
        LocalDateTime memoUpdatedAt
) {
}