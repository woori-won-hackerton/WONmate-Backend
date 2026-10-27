package wonit.hackathon.a1.domain.consult.service;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import wonit.hackathon.a1.domain.consult.dto.request.ConsultRecordUpdateRequest;
import wonit.hackathon.a1.domain.consult.dto.response.ConsultAiRecord;
import wonit.hackathon.a1.domain.consult.dto.response.ConsultRecordResponse;
import wonit.hackathon.a1.domain.consult.entity.Consult;
import wonit.hackathon.a1.domain.consult.enums.ConsultationStatus;
import wonit.hackathon.a1.domain.consult.repository.ConsultRepository;

@Service
@RequiredArgsConstructor
public class ConsultRecordStoreService {

    private final ConsultRepository consultRepository;
    private final ObjectMapper objectMapper;

    public record Snapshot(
            String memo,
            LocalDateTime memoUpdatedAt,
            ConsultAiRecord existingRecord
    ) {
    }

    @Transactional
    public Snapshot load(Long fcId, Long consultationId) {
        Consult consult = findOwned(fcId, consultationId);
        requireCompleted(consult);

        ConsultAiRecord existing = consult.getAiRecord() == null
                ? null
                : objectMapper.treeToValue(
                consult.getAiRecord(),
                ConsultAiRecord.class
        );

        return new Snapshot(
                consult.getMemo(),
                consult.getMemoUpdatedAt(),
                existing
        );
    }

    @Transactional
    public ConsultAiRecord saveGenerated(
            Long fcId,
            Long consultationId,
            Snapshot source,
            ConsultAiRecord generated
    ) {
        Consult consult = findOwned(fcId, consultationId);
        requireCompleted(consult);

        // AI 호출 중 메모가 바뀌었으면 오래된 메모 기반 결과는 저장하지 않음
        if (!Objects.equals(consult.getMemo(), source.memo())
                || !Objects.equals(
                consult.getMemoUpdatedAt(),
                source.memoUpdatedAt()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "생성 중 메모가 변경되었습니다. 다시 생성해주세요."
            );
        }

        // 다른 요청이 먼저 저장했으면 기존 기록을 보존
        if (consult.getAiRecord() != null) {
            return objectMapper.treeToValue(
                    consult.getAiRecord(),
                    ConsultAiRecord.class
            );
        }

        consult.updateAiRecord(objectMapper.valueToTree(generated));
        return generated;
    }

    private Consult findOwned(Long fcId, Long consultationId) {
        return consultRepository
                .findOwnedForCompletion(consultationId, fcId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "상담을 찾을 수 없습니다."
                ));
    }

    private void requireCompleted(Consult consult) {
        if (consult.getStatus() != ConsultationStatus.completed) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "완료된 상담만 AI 기록을 생성하거나 조회할 수 있습니다."
            );
        }
    }

    @Transactional
    public ConsultRecordResponse update(
            Long fcId,
            Long consultationId,
            ConsultRecordUpdateRequest request
    ) {
        Consult consult = findOwned(fcId, consultationId);
        requireCompleted(consult);
        requireRecordExists(consult);

        if (consult.getRecordConfirmedAt() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "확정된 상담 기록은 수정할 수 없습니다."
            );
        }

        consult.updateAiRecord(
                objectMapper.valueToTree(request.toRecord())
        );

        return toResponse(consult);
    }

    @Transactional
    public ConsultRecordResponse confirm(
            Long fcId,
            Long consultationId,
            ConsultRecordUpdateRequest request
    ) {
        Consult consult = findOwned(fcId, consultationId);
        requireCompleted(consult);
        requireRecordExists(consult);

        ConsultAiRecord requestedRecord = request.toRecord();

        if (consult.getRecordConfirmedAt() != null) {
            ConsultAiRecord savedRecord = objectMapper.treeToValue(
                    consult.getAiRecord(),
                    ConsultAiRecord.class
            );

            // 동일 요청의 재시도는 성공 처리
            if (savedRecord.equals(requestedRecord)) {
                return toResponse(consult);
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "이미 확정된 기록과 요청 내용이 다릅니다."
            );
        }

        // 화면에서 마지막으로 수정한 내용까지 저장한 후 확정
        consult.updateAiRecord(
                objectMapper.valueToTree(requestedRecord)
        );
        consult.confirmAiRecord();

        return toResponse(consult);
    }

    @Transactional
    public ConsultRecordResponse getDetail(
            Long fcId,
            Long consultationId
    ) {
        Consult consult = findOwned(fcId, consultationId);
        requireCompleted(consult);
        requireRecordExists(consult);

        return toResponse(consult);
    }

    private void requireRecordExists(Consult consult) {
        if (consult.getAiRecord() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "AI 상담 기록 초안을 먼저 생성해주세요."
            );
        }
    }

    private ConsultRecordResponse toResponse(Consult consult) {
        ConsultAiRecord record = objectMapper.treeToValue(
                consult.getAiRecord(),
                ConsultAiRecord.class
        );

        return new ConsultRecordResponse(
                consult.getId(),
                record,
                consult.getRecordConfirmedAt() != null,
                consult.getRecordConfirmedAt()
        );
    }
}