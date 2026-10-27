package wonit.hackathon.a1.domain.consult.service;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.json.JsonMapper;

import wonit.hackathon.a1.domain.ai.briefing.dto.BriefingAiResult;
import wonit.hackathon.a1.domain.ai.briefing.dto.BriefingInput;
import wonit.hackathon.a1.domain.consult.dto.response.BriefingResponse;
import wonit.hackathon.a1.domain.consult.entity.Consult;
import wonit.hackathon.a1.domain.consult.enums.ConsultationStatus;
import wonit.hackathon.a1.domain.consult.repository.ConsultRepository;
import wonit.hackathon.a1.domain.consultprepare.entity.ConsultPrepare;
import wonit.hackathon.a1.domain.consultprepare.repository.ConsultPrepareRepository;
import wonit.hackathon.a1.domain.customerwiki.entity.CustomerWiki;
import wonit.hackathon.a1.domain.customerwiki.repository.CustomerWikiRepository;
import wonit.hackathon.a1.domain.fccustomer.entity.FcCustomer;
import wonit.hackathon.a1.domain.fccustomer.enums.AssignmentStatus;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BriefingDataServiceImpl implements BriefingDataService {

        private final ConsultRepository consultRepository;
        private final CustomerWikiRepository customerWikiRepository;
        private final ConsultPrepareRepository consultPrepareRepository;
        private final JsonMapper jsonMapper;

        @Override
        @Transactional(readOnly = true)
        public BriefingInput loadInput(
                        Long fcId,
                        Long consultationId) {
                Consult consult = consultRepository.findById(consultationId)
                                .orElseThrow(this::consultNotFound);

                validateAccess(consult, fcId);

                FcCustomer fcCustomer = consult.getFcCustomer();
                Long customerId = fcCustomer.getCustomer().getId();

                String wikiContent = customerWikiRepository
                                .findByCustomer_Id(customerId)
                                .map(CustomerWiki::getContent)
                                .orElse("");

                OffsetDateTime now = OffsetDateTime.now();
                OffsetDateTime scheduledAt = consult.getScheduledAt();

                // 과거 상담 재생성 시에는 그 상담의 예약 시각 이전만 조회.
                // 미래 예약이나 예약 시각 미정이면 현재 시각 이전만 조회.
                OffsetDateTime cutoff = scheduledAt != null && scheduledAt.isBefore(now)
                                ? scheduledAt
                                : now;

                var histories = consultRepository.findBriefingHistories(
                                fcCustomer.getId(),
                                consult.getId(),
                                ConsultationStatus.completed,
                                cutoff,
                                PageRequest.of(0, 10))
                                .stream()
                                .map(BriefingInput.ConsultationHistory::from)
                                .toList();

                return new BriefingInput(
                                wikiContent,
                                histories);
        }

        @Override
        @Transactional
        public void saveBriefing(
                        Long fcId,
                        Long consultationId,
                        BriefingAiResult result) {
                Consult consult = consultRepository
                                .findByIdForBriefingUpdate(consultationId)
                                .orElseThrow(this::consultNotFound);

                validateAccess(consult, fcId);

                String json = jsonMapper.writeValueAsString(result);

                var existing = consultPrepareRepository
                                .findByConsult_Id(consultationId);

                if (existing.isPresent()) {
                        existing.get().updateSummaryBriefing(json);
                } else {
                        consultPrepareRepository.save(
                                        ConsultPrepare.create(consult, json));
                }
        }

        @Override
        @Transactional(readOnly = true)
        public Optional<BriefingResponse> loadSavedBriefing(
                        Long fcId,
                        Long consultationId) {
                Consult consult = consultRepository.findById(consultationId)
                                .orElseThrow(this::consultNotFound);

                validateAccess(consult, fcId);

                return consultPrepareRepository
                                .findByConsult_Id(consultationId)
                                .filter(prepare -> prepare.getSummaryBriefing() != null
                                                && !prepare.getSummaryBriefing().isBlank())
                                .map(prepare -> BriefingResponse.of(
                                                jsonMapper.readValue(
                                                                prepare.getSummaryBriefing(),
                                                                BriefingAiResult.class),
                                                prepare.getUpdatedAt()));
        }

        private void validateAccess(Consult consult, Long fcId) {
                FcCustomer fcCustomer = consult.getFcCustomer();

                boolean owner = Objects.equals(
                                fcCustomer.getUser().getId(),
                                fcId);

                boolean active = fcCustomer.getAssignmentStatus() == AssignmentStatus.active;

                if (!owner || !active) {
                        throw new ResponseStatusException(
                                        HttpStatus.FORBIDDEN,
                                        "해당 고객의 상담에 접근할 권한이 없습니다.");
                }
        }

        private ResponseStatusException consultNotFound() {
                return new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "상담을 찾을 수 없습니다.");
        }
}