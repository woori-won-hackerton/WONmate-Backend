package wonit.hackathon.a1.domain.todo.code;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import wonit.hackathon.a1.global.code.BaseErrorCode;


@Getter
@AllArgsConstructor
public enum TodoErrorCode implements BaseErrorCode {

    TODO_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "TODO_4041",
            "조회 가능한 할 일을 찾을 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}