package wonit.hackathon.a1.global.security.code;


import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import wonit.hackathon.a1.global.code.BaseErrorCode;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
    TOKEN_NOT_EXIST(
            HttpStatus.UNAUTHORIZED,
            "TOKEN_NOT_EXIST",
            "인증 토큰이 필요합니다."
    ),

    TOKEN_EXPIRED(
            HttpStatus.UNAUTHORIZED,
            "TOKEN_EXPIRED",
            "토큰이 만료되었습니다."
    ),

    TOKEN_INVALID(
            HttpStatus.UNAUTHORIZED,
            "TOKEN_INVALID",
            "유효하지 않은 토큰입니다."
    ),
    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "ACCESS_DENIED",
            "액세스가 제한되었습니다."
    ),
    LOGIN_FAILED(
            HttpStatus.UNAUTHORIZED,
            "LOGIN_FAILED",
            "아이디 또는 비밀번호가 올바르지 않습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
