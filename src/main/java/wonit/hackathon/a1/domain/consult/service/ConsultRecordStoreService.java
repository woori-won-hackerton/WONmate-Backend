package wonit.hackathon.a1.domain.consult.service;

import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import wonit.hackathon.a1.domain.consult.dto.response.ConsultAiRecord;
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
}