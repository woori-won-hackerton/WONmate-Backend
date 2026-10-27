package wonit.hackathon.a1.domain.customer.dto.response;

import wonit.hackathon.a1.domain.customer.entity.Customer;
import wonit.hackathon.a1.domain.customer.enums.Gender;

import java.time.LocalDate;
import java.time.Period;

public record CustomerResponse(
        Long customerId,
        String fullName,
        LocalDate birthDate,
        Integer age,
        Gender gender,
        String occupation,
        Boolean isMarried,
        Boolean hasChildren,
        Boolean hasDisability,
        String phoneNumber,
        String residentialArea,
        /** 가장 최근 완료한 상담 날짜. 없으면 null */
        LocalDate lastConsultedAt,
        /** 완료한 상담 수 */
        long completedConsultCount) {
    public static CustomerResponse from(
            Customer customer,
            LocalDate today) {
        return from(customer, today, null, 0L);
    }

    public static CustomerResponse from(
            Customer customer,
            LocalDate today,
            LocalDate lastConsultedAt,
            long completedConsultCount) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getBirthDate(),
                calculateAge(customer.getBirthDate(), today),
                customer.getGender(),
                customer.getOccupation(),
                customer.getIsMarried(),
                customer.getHasChildren(),
                customer.getHasDisability(),
                customer.getPhoneNumber(),
                customer.getResidentialArea(),
                lastConsultedAt,
                completedConsultCount);
    }

    private static Integer calculateAge(
            LocalDate birthDate,
            LocalDate today) {
        if (birthDate == null || birthDate.isAfter(today)) {
            return null;
        }

        return Period.between(birthDate, today).getYears();
    }
}