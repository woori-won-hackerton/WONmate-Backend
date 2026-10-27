package wonit.hackathon.a1.global.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GeneralErrorCode implements BaseErrorCode {

    //서버 내부 에러
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_5001", "서버 내부 오류입니다."),
    INVALID_PARAMETER(
            HttpStatus.BAD_REQUEST,
            "COMMON4000",
            "잘못된 요청입니다."
    ),

    //유저 에러
    USER_NOT_FOUND(HttpStatus.NOT_FOUND,"USER_4041","유저를 찾을 수 없습니다.");


    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

}
