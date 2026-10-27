package wonit.hackathon.a1.domain.consult.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import wonit.hackathon.a1.domain.consult.dto.response.ConsultAiRecord;

@Service
@RequiredArgsConstructor
public class ConsultRecordService {

    private final ConsultRecordStoreService storeService;
    private final ConsultRecordAiService aiService;

    public ConsultAiRecord generate(Long fcId, Long consultationId) {
        var source = storeService.load(fcId, consultationId);

        if (source.existingRecord() != null) {
            return source.existingRecord();
        }

        if (source.memo() == null || source.memo().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "저장된 상담 메모가 없어 AI 기록을 생성할 수 없습니다.");
        }

        ConsultAiRecord generated = aiService.generate(source.memo());

        return storeService.saveGenerated(
                fcId,
                consultationId,
                source,
                generated);
    }

    public ConsultAiRecord get(Long fcId, Long consultationId) {
        var source = storeService.load(fcId, consultationId);

        if (source.existingRecord() == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "아직 생성된 AI 상담 기록이 없습니다.");
        }

        return source.existingRecord();
    }

    public ConsultAiRecord update(
            Long fcId,
            Long consultationId,
            ConsultAiRecord record) {
        return storeService.update(fcId, consultationId, record);
    }

    public ConsultAiRecord confirm(Long fcId, Long consultationId) {
        return storeService.confirm(fcId, consultationId);
    }
}