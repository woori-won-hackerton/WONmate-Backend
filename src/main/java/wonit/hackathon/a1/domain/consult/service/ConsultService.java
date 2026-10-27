package wonit.hackathon.a1.domain.consult.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import wonit.hackathon.a1.domain.consult.dto.request.ConsultCreateRequest;
import wonit.hackathon.a1.domain.consult.dto.response.ConsultResponse;
import wonit.hackathon.a1.domain.consult.entity.Consult;
import wonit.hackathon.a1.domain.consult.enums.ConsultationStatus;
import wonit.hackathon.a1.domain.consult.repository.ConsultRepository;
import wonit.hackathon.a1.domain.fccustomer.entity.FcCustomer;
import wonit.hackathon.a1.domain.fccustomer.repository.FcCustomerRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultService {

        private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

        /** 기간 조회 최대 일수 */
        private static final long MAX_RANGE_DAYS = 92;

        /** 완료된 상담 목록 최대 건수 */
        private static final int MAX_RECORDS = 200;

        private final ConsultRepository consultRepository;
        private final FcCustomerRepository fcCustomerRepository;

        // 1. 상담 일정 생성
        @Transactional
        public ConsultResponse createConsult(
                        Long userId,
                        ConsultCreateRequest request) {
                // 로그인한 FC가 담당하는 고객인지 확인
                FcCustomer fcCustomer = fcCustomerRepository
                                .findByUserIdAndCustomerId(userId, request.customerId())
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "담당 고객을 찾을 수 없습니다."));

                OffsetDateTime scheduledAt = request.consultDate()
                                .atTime(request.consultTime())
                                .atZone(SEOUL)
                                .toOffsetDateTime();

                Consult consult = Consult.create(
                                fcCustomer,
                                scheduledAt,
                                request.consultType(),
                                null, // 구체적인 상담 목적
                                null, // 상담 메모
                                null, // AI 상담 기록
                                null // 일정 확정 시각
                );

                Consult savedConsult = consultRepository.save(consult);

                return toResponse(savedConsult);
        }

        // 2. 상담 일정 리스트 조회 — 특정 날짜(date) 또는 기간(from ~ to, 양 끝 포함)
        public List<ConsultResponse> getConsults(
                        Long userId,
                        LocalDate date,
                        LocalDate from,
                        LocalDate to,
                        ConsultationStatus status) {
                LocalDate start;
                LocalDate end;

                if (date != null) {
                        start = date;
                        end = date;
                } else {
                        if (from == null || to == null) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "date 또는 from · to를 입력해주세요.");
                        }
                        if (to.isBefore(from)) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "to는 from보다 빠를 수 없습니다.");
                        }
                        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
                                throw new ResponseStatusException(
                                                HttpStatus.BAD_REQUEST,
                                                "기간 조회는 최대 " + MAX_RANGE_DAYS + "일까지 가능합니다.");
                        }
                        start = from;
                        end = to;
                }

                OffsetDateTime startAt = start
                                .atStartOfDay(SEOUL)
                                .toOffsetDateTime();

                OffsetDateTime endAt = end.plusDays(1)
                                .atStartOfDay(SEOUL)
                                .toOffsetDateTime();

                return consultRepository
                                .findAllByUserIdAndScheduledRange(userId, startAt, endAt)
                                .stream()
                                .filter(consult -> status == null || consult.getStatus() == status)
                                .map(this::toResponse)
                                .toList();
        }

        // 3. 상담 일정 단건 조회
        public ConsultResponse getConsult(
                        Long userId,
                        Long consultId) {
                Consult consult = consultRepository
                                .findByIdAndUserId(consultId, userId)
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "상담 일정을 찾을 수 없습니다."));

                return toResponse(consult);
        }

        // 4. 완료된 상담 목록 (최근 순) — AI 상담 요약 목록 · 고객별 상담 기록
        public List<ConsultResponse> getCompletedConsults(
                        Long userId,
                        Long customerId) {
                PageRequest limit = PageRequest.of(0, MAX_RECORDS);

                List<Consult> consults = customerId == null
                                ? consultRepository.findAllByUserIdAndStatus(
                                                userId,
                                                ConsultationStatus.completed,
                                                limit)
                                : consultRepository.findAllByUserIdAndCustomerIdAndStatus(
                                                userId,
                                                customerId,
                                                ConsultationStatus.completed,
                                                limit);

                return consults.stream()
                                .map(this::toResponse)
                                .toList();
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

                Integer customerAge = customer.getBirthDate() == null || consultDate == null
                                ? null
                                : Period.between(
                                                customer.getBirthDate(),
                                                consultDate).getYears();

                return new ConsultResponse(
                                consult.getId(),
                                customer.getId(),
                                customer.getFullName(),
                                customerAge,
                                consult.getConsultType(),
                                consult.getConsultType().getDescription(),
                                consultDate,
                                scheduledAt == null ? null : scheduledAt.toLocalTime(),
                                consult.getStatus(),
                                consult.getAiRecordStatus());
        }
}