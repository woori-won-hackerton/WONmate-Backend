package wonit.hackathon.a1.domain.consult.service;



import wonit.hackathon.a1.domain.ai.briefing.dto.BriefingAiResult;
import wonit.hackathon.a1.domain.ai.briefing.dto.BriefingInput;

public interface BriefingDataService {

    BriefingInput loadInput(Long fcId, Long consultationId);

    void saveBriefing(
            Long fcId,
            Long consultationId,
            BriefingAiResult result
    );
}