package wonit.hackathon.a1.domain.consult.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import wonit.hackathon.a1.domain.consult.dto.request.ConsultCreateRequest;
import wonit.hackathon.a1.domain.consult.dto.response.ConsultResponse;
import wonit.hackathon.a1.domain.consult.entity.Consult;
import wonit.hackathon.a1.domain.consult.repository.ConsultRepository;
import wonit.hackathon.a1.domain.fccustomer.entity.FcCustomer;
import wonit.hackathon.a1.domain.fccustomer.repository.FcCustomerRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final ConsultRepository consultRepository;
    private final FcCustomerRepository fcCustomerRepository;

    // 1. 상담 일정 생성
    @Transactional
    public ConsultResponse createConsult(
            Long userId,
            ConsultCreateRequest request
    ) {
        // 로그인한 FC가 담당하는 고객인지 확인
        FcCustomer fcCustomer = fcCustomerRepository
                .findByUserIdAndCustomerId(userId, request.customerId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "담당 고객을 찾을 수 없습니다."
                ));

        OffsetDateTime scheduledAt = request.consultDate()
                .atTime(request.consultTime())
                .atZone(SEOUL)
                .toOffsetDateTime();

        Consult consult = Consult.create(
                fcCustomer,
                scheduledAt,
                request.consultType(),
                null,   // 구체적인 상담 목적
                null,   // 상담 메모
                null,   // AI 상담 기록
                null    // 일정 확정 시각
        );

        Consult savedConsult = consultRepository.save(consult);

        return toResponse(savedConsult);
    }

    // 2. 특정 날짜의 상담 일정 리스트 조회
    public List<ConsultResponse> getConsults(
            Long userId,
            LocalDate date
    ) {
        OffsetDateTime startAt = date
                .atStartOfDay(SEOUL)
                .toOffsetDateTime();

        OffsetDateTime endAt = date.plusDays(1)
                .atStartOfDay(SEOUL)
                .toOffsetDateTime();

        return consultRepository
                .findAllByUserIdAndScheduledRange(userId, startAt, endAt)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // 3. 상담 일정 단건 조회
    public ConsultResponse getConsult(
            Long userId,
            Long consultId
    ) {
        Consult consult = consultRepository
                .findByIdAndUserId(consultId, userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "상담 일정을 찾을 수 없습니다."
                ));

        return toResponse(consult);
    }

    private ConsultResponse toResponse(Consult consult) {
        var customer = consult.getFcCustomer().getCustomer();

        // DB가 다른 offset으로 반환하더라도 화면에는 한국 시간으로 제공
        var scheduledAt = consult.getScheduledAt() == null
                ? null
                : consult.getScheduledAt().atZoneSameInstant(SEOUL);

        LocalDate consultDate = scheduledAt == null
                ? null
                : scheduledAt.toLocalDate();

        Integer customerAge =
                customer.getBirthDate() == null || consultDate == null
                        ? null
                        : Period.between(
                        customer.getBirthDate(),
                        consultDate
                ).getYears();

        return new ConsultResponse(
                consult.getId(),
                customer.getId(),
                customer.getFullName(),
                customerAge,
                consult.getConsultType(),
                consult.getConsultType().getDescription(),
                consultDate,
                scheduledAt == null ? null : scheduledAt.toLocalTime()
        );
    }
}