package wonit.hackathon.a1.domain.consult.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalTime;
import wonit.hackathon.a1.domain.consult.enums.ConsultType;

@Schema(description = "상담 일정 생성 요청")
public record ConsultCreateRequest(

        @Schema(description = "고객 ID", example = "1")
        @NotNull(message = "고객을 선택해주세요.")
        @Positive(message = "고객 ID는 양수여야 합니다.")
        Long customerId,

        @Schema(description = "상담 종류", example = "NEW")
        @NotNull(message = "상담 종류를 선택해주세요.")
        ConsultType consultType,

        @Schema(
                description = "상담 날짜",
                type = "string",
                example = "2026-10-27"
        )
        @NotNull(message = "상담 날짜를 입력해주세요.")
        LocalDate consultDate,

        @Schema(
                description = "상담 시간",
                type = "string",
                example = "10:00:00"
        )
        @NotNull(message = "상담 시간을 입력해주세요.")
        LocalTime consultTime

) {
}