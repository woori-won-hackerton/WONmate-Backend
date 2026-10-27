package wonit.hackathon.a1.domain.consult.service;

import wonit.hackathon.a1.domain.ai.briefing.dto.BriefingAiResult;
import wonit.hackathon.a1.domain.ai.briefing.dto.BriefingInput;
import wonit.hackathon.a1.domain.consult.dto.response.BriefingResponse;

import java.util.Optional;

public interface BriefingDataService {

    BriefingInput loadInput(Long fcId, Long consultationId);

    void saveBriefing(
            Long fcId,
            Long consultationId,
            BriefingAiResult result);

    /** 마지막으로 생성해 저장한 브리핑 (없으면 empty) */
    Optional<BriefingResponse> loadSavedBriefing(
            Long fcId,
            Long consultationId);
}