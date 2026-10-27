package wonit.hackathon.a1.global.exception;


import lombok.Getter;
import wonit.hackathon.a1.global.code.BaseErrorCode;

@Getter
//비즈니스 로직에서 오류가 발생했음을 알리는 역할
public class GeneralException extends RuntimeException{

    //예외에서 발생한 상세 내용
    private final BaseErrorCode errorCode;

    //생성자
    public GeneralException(BaseErrorCode code) {
        this.errorCode = code;
    }

    public GeneralException(BaseErrorCode code, String message) {
        super(message);
        this.errorCode = code;
    }


}
