package wonit.hackathon.a1.domain.consult.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import wonit.hackathon.a1.domain.ai.briefing.dto.BriefingAiResult;

@Schema(description = "저장된 AI 브리핑")
public record BriefingResponse(

        @Schema(description = "요약 (1~5개)") List<String> summary,

        @Schema(description = "이번 상담에서 확인할 정보 (0~5개)") List<String> unconfirmedInformation,

        @Schema(description = "상담 팁 (1~5개)") List<String> consultationTips,

        @Schema(description = "예상 질문 (예전에 만든 브리핑에는 빈 배열)") List<String> expectedQuestions,

        @Schema(description = "브리핑을 마지막으로 생성한 시각", example = "2026-10-28T10:30:00") LocalDateTime generatedAt) {

    public static BriefingResponse of(
            BriefingAiResult result,
            LocalDateTime generatedAt) {
        return new BriefingResponse(
                orEmpty(result.summary()),
                orEmpty(result.unconfirmedInformation()),
                orEmpty(result.consultationTips()),
                orEmpty(result.expectedQuestions()),
                generatedAt);
    }

    private static List<String> orEmpty(List<String> list) {
        return list == null ? List.of() : list;
    }
}