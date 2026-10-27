package wonit.hackathon.a1.domain.consult.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;

import wonit.hackathon.a1.domain.ai.briefing.dto.BriefingAiResult;
import wonit.hackathon.a1.domain.ai.briefing.dto.BriefingInput;
import wonit.hackathon.a1.domain.ai.briefing.service.BriefingAiService;
import wonit.hackathon.a1.domain.consult.dto.response.BriefingResponse;

@Service
@RequiredArgsConstructor
public class ConsultBriefingService {

        private final BriefingDataService briefingDataService;
        private final BriefingAiService briefingAiService;

        public BriefingAiResult generateBriefing(
                        Long fcId,
                        Long consultationId) {
                BriefingInput input = briefingDataService.loadInput(
                                fcId,
                                consultationId);

                BriefingAiResult result = briefingAiService.generate(input);

                briefingDataService.saveBriefing(
                                fcId,
                                consultationId,
                                result);

                return result;
        }

        /** 저장된 브리핑 조회 — 아직 생성하지 않았으면 404 */
        public BriefingResponse getSavedBriefing(
                        Long fcId,
                        Long consultationId) {
                return briefingDataService
                                .loadSavedBriefing(fcId, consultationId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "아직 생성된 브리핑이 없습니다."));
        }
}