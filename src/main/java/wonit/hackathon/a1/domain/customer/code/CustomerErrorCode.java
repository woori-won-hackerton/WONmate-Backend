package wonit.hackathon.a1.domain.customer.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import wonit.hackathon.a1.global.code.BaseErrorCode;


@Getter
@AllArgsConstructor
public enum CustomerErrorCode implements BaseErrorCode {

    CUSTOMER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CUSTOMER_4041",
            "조회 가능한 고객을 찾을 수 없습니다."
    ),

    INVALID_PAGE_REQUEST(
            HttpStatus.BAD_REQUEST,
            "CUSTOMER_4001",
            "page는 0 이상, size는 1 이상 100 이하여야 합니다."
    ),

    INVALID_SEARCH_NAME(
            HttpStatus.BAD_REQUEST,
            "CUSTOMER_4002",
            "검색할 고객 이름은 100자 이하여야 합니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}