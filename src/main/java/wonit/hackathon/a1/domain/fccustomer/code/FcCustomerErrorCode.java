package wonit.hackathon.a1.domain.fccustomer.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import wonit.hackathon.a1.global.code.BaseErrorCode;

@Getter
@AllArgsConstructor
public enum FcCustomerErrorCode implements BaseErrorCode {

    ASSIGNMENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "FC_CUSTOMER_4041",
            "접근 가능한 담당 고객 관계를 찾을 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}