package wonit.hackathon.a1.global.apiPayload;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import wonit.hackathon.a1.global.code.BaseErrorCode;


@Getter
@AllArgsConstructor
@JsonPropertyOrder({"isSuccess","code","message","result","error"})
public class ApiResponse<T> {

    @JsonProperty("isSuccess")
    private Boolean isSuccess;

    @JsonProperty("code")
    private String code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("result")
    private final T result;

    @JsonProperty("error")
    private Object error;

    //실패 응답
    public static <T> ApiResponse<T> onFailure(BaseErrorCode errorCode, Object error)
    {
        return new ApiResponse<>(false, errorCode.getCode(),errorCode.getMessage(),null,error);
    }

}
