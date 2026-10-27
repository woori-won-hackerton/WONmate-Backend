package wonit.hackathon.a1.domain.consult.service;


import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import wonit.hackathon.a1.domain.consult.dto.request.ConsultCompleteRequest;
import wonit.hackathon.a1.domain.consult.entity.Consult;
import wonit.hackathon.a1.domain.consult.enums.ConsultationStatus;
import wonit.hackathon.a1.domain.consult.repository.ConsultRepository;


@Service
@RequiredArgsConstructor
public class ConsultCompleteService {

    private final ConsultRepository consultRepository;

    @Transactional
    public void complete(
            Long fcId,
            Long consultationId,
            ConsultCompleteRequest request
    ) {
        Consult consult = consultRepository
                .findOwnedForCompletion(consultationId, fcId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "상담을 찾을 수 없습니다."
                ));

        if (consult.getStatus() == ConsultationStatus.completed) {
            return;
        }

        if (consult.getStatus() == ConsultationStatus.cancelled) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "취소된 상담은 완료할 수 없습니다."
            );
        }

        consult.complete(request.memo());
    }
}