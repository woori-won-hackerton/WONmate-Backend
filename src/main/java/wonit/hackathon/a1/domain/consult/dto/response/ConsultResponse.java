package wonit.hackathon.a1.domain.consult.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;
import wonit.hackathon.a1.domain.consult.enums.ConsultType;

@Schema(description = "상담 일정 응답")
public record ConsultResponse(

        @Schema(description = "상담 ID", example = "1")
        Long consultId,

        @Schema(description = "고객 ID", example = "1")
        Long customerId,

        @Schema(description = "고객 이름", example = "박준호")
        String customerName,

        @Schema(description = "상담 날짜 기준 고객 만 나이", example = "42")
        Integer customerAge,

        @Schema(description = "상담 종류 코드", example = "NEW")
        ConsultType consultType,

        @Schema(description = "상담 종류 표시명", example = "신규 상담")
        String consultTypeName,

        @Schema(description = "상담 날짜", example = "2026-10-27")
        LocalDate consultDate,

        @Schema(description = "상담 시간", example = "13:30:00")
        LocalTime consultTime

) {
}