package wonit.hackathon.a1.domain.consult.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import wonit.hackathon.a1.domain.ai.briefing.dto.BriefingAiResult;
import wonit.hackathon.a1.domain.ai.briefing.dto.BriefingInput;
import wonit.hackathon.a1.domain.ai.briefing.service.BriefingAiService;

@Service
@RequiredArgsConstructor
public class ConsultBriefingService {

    private final BriefingDataService briefingDataService;
    private final BriefingAiService briefingAiService;

    public BriefingAiResult generateBriefing(
            Long fcId,
            Long consultationId
    ) {
        BriefingInput input = briefingDataService.loadInput(
                fcId,
                consultationId
        );

        BriefingAiResult result = briefingAiService.generate(input);

        briefingDataService.saveBriefing(
                fcId,
                consultationId,
                result
        );

        return result;
    }
}