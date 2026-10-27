package wonit.hackathon.a1.domain.customer.service;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import wonit.hackathon.a1.domain.consult.enums.ConsultationStatus;
import wonit.hackathon.a1.domain.consult.repository.ConsultRepository;
import wonit.hackathon.a1.domain.customer.code.CustomerErrorCode;
import wonit.hackathon.a1.domain.customer.dto.response.CustomerPageResponse;
import wonit.hackathon.a1.domain.customer.dto.response.CustomerResponse;
import wonit.hackathon.a1.domain.customer.entity.Customer;
import wonit.hackathon.a1.domain.customer.repository.CustomerRepository;
import wonit.hackathon.a1.domain.fccustomer.enums.AssignmentStatus;
import wonit.hackathon.a1.global.exception.GeneralException;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

        private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

        private final CustomerRepository customerRepository;
        private final ConsultRepository consultRepository;

        /** 고객별 완료 상담 통계 */
        private record ConsultStats(
                        LocalDate lastConsultedAt,
                        long completedCount) {
                static final ConsultStats EMPTY = new ConsultStats(null, 0L);
        }

        public CustomerPageResponse getCustomers(
                        @NotNull @Positive Long fcId,
                        String name,
                        int page,
                        int size) {
                validatePageRequest(page, size);

                String namePattern = createNamePattern(name);

                PageRequest pageable = PageRequest.of(
                                page,
                                size,
                                Sort.by(
                                                Sort.Order.asc("fullName"),
                                                Sort.Order.asc("id")));

                LocalDate today = LocalDate.now(SERVICE_ZONE);

                Page<Customer> customers = customerRepository.findAssignedCustomers(
                                fcId,
                                AssignmentStatus.active,
                                namePattern,
                                pageable);

                Map<Long, ConsultStats> stats = loadConsultStats(
                                fcId,
                                customers.getContent().stream()
                                                .map(Customer::getId)
                                                .toList());

                Page<CustomerResponse> result = customers.map(customer -> toResponse(
                                customer,
                                today,
                                stats.getOrDefault(customer.getId(), ConsultStats.EMPTY)));

                return CustomerPageResponse.from(result);
        }

        public CustomerResponse getCustomer(
                        @NotNull @Positive Long fcId,
                        @NotNull @Positive Long customerId) {
                Customer customer = customerRepository.findAssignedCustomer(
                                fcId,
                                customerId,
                                AssignmentStatus.active).orElseThrow(
                                                () -> new GeneralException(
                                                                CustomerErrorCode.CUSTOMER_NOT_FOUND));

                ConsultStats stats = loadConsultStats(fcId, List.of(customerId))
                                .getOrDefault(customerId, ConsultStats.EMPTY);

                return toResponse(
                                customer,
                                LocalDate.now(SERVICE_ZONE),
                                stats);
        }

        private CustomerResponse toResponse(
                        Customer customer,
                        LocalDate today,
                        ConsultStats stats) {
                return CustomerResponse.from(
                                customer,
                                today,
                                stats.lastConsultedAt(),
                                stats.completedCount());
        }

        /** 완료된 상담 수 · 가장 최근 상담 날짜를 고객별로 한 번에 조회 */
        private Map<Long, ConsultStats> loadConsultStats(
                        Long fcId,
                        List<Long> customerIds) {
                Map<Long, ConsultStats> result = new HashMap<>();
                if (customerIds.isEmpty()) {
                        return result;
                }

                List<Object[]> rows = consultRepository.findConsultStatsByCustomer(
                                fcId,
                                customerIds,
                                ConsultationStatus.completed);

                for (int i = 0; i < rows.size(); i++) {
                        Object[] row = rows.get(i);
                        Long customerId = ((Number) row[0]).longValue();
                        long count = ((Number) row[1]).longValue();
                        result.put(customerId, new ConsultStats(
                                        toServiceDate(row[2]),
                                        count));
                }

                return result;
        }

        /** DB 드라이버마다 시각 타입이 달라질 수 있어 한국 날짜로 통일 */
        private LocalDate toServiceDate(Object value) {
                if (value == null) {
                        return null;
                }
                if (value instanceof OffsetDateTime offsetDateTime) {
                        return offsetDateTime.atZoneSameInstant(SERVICE_ZONE).toLocalDate();
                }
                if (value instanceof Instant instant) {
                        return instant.atZone(SERVICE_ZONE).toLocalDate();
                }
                if (value instanceof Timestamp timestamp) {
                        return timestamp.toInstant().atZone(SERVICE_ZONE).toLocalDate();
                }
                if (value instanceof LocalDateTime localDateTime) {
                        return localDateTime.toLocalDate();
                }
                return null;
        }

        private void validatePageRequest(int page, int size) {
                if (page < 0 || size < 1 || size > 100) {
                        throw new GeneralException(
                                        CustomerErrorCode.INVALID_PAGE_REQUEST);
                }
        }

        private String createNamePattern(String name) {
                if (name == null || name.isBlank()) {
                        return "%";
                }

                String keyword = name.strip();

                if (keyword.length() > 100) {
                        throw new GeneralException(
                                        CustomerErrorCode.INVALID_SEARCH_NAME);
                }

                String escapedKeyword = keyword
                                .replace("!", "!!")
                                .replace("%", "!%")
                                .replace("_", "!_");

                return "%" + escapedKeyword + "%";
        }
}