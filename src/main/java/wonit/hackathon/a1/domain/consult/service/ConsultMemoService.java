package wonit.hackathon.a1.domain.consult.service;



import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import wonit.hackathon.a1.domain.consult.dto.request.ConsultMemoUpdateRequest;
import wonit.hackathon.a1.domain.consult.dto.response.ConsultMemoResponse;
import wonit.hackathon.a1.domain.consult.entity.Consult;
import wonit.hackathon.a1.domain.consult.repository.ConsultRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultMemoService {

    private final ConsultRepository consultRepository;

    public ConsultMemoResponse getMemo(
            Long userId,
            Long consultationId
    ) {
        Consult consult = getAuthorizedConsult(userId, consultationId);

        return toResponse(consultationId, consult);
    }

    @Transactional
    public ConsultMemoResponse updateMemo(
            Long userId,
            Long consultationId,
            ConsultMemoUpdateRequest request
    ) {
        Consult consult = getAuthorizedConsult(userId, consultationId);

        consult.updateMemo(request.memo());

        return toResponse(consultationId, consult);
    }

    private Consult getAuthorizedConsult(
            Long userId,
            Long consultationId
    ) {
        Consult consult = consultRepository.findById(consultationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "상담을 찾을 수 없습니다."
                ));

        // 실제 엔티티의 연관관계 필드명에 맞춰 수정
        Long ownerId = consult.getFcCustomer().getUser().getId();

        if (!ownerId.equals(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "해당 상담에 접근할 권한이 없습니다."
            );
        }

        return consult;
    }

    private ConsultMemoResponse toResponse(
            Long consultationId,
            Consult consult
    ) {
        return new ConsultMemoResponse(
                consultationId,
                consult.getMemo() == null ? "" : consult.getMemo(),
                consult.getMemoUpdatedAt()
        );
    }
}