package wonit.hackathon.a1.global.handler;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import wonit.hackathon.a1.global.apiPayload.ApiResponse;
import wonit.hackathon.a1.global.code.BaseErrorCode;
import wonit.hackathon.a1.global.code.GeneralErrorCode;
import wonit.hackathon.a1.global.exception.GeneralException;
import wonit.hackathon.a1.global.security.code.AuthErrorCode;

import javax.naming.AuthenticationException;
import java.util.List;

@Slf4j
@RestControllerAdvice(annotations = RestController.class)
public class GlobalExceptionHandler {

        /**
         * 직접 정의한 비즈니스 예외 처리
         */
        @ExceptionHandler(GeneralException.class)
        public ResponseEntity<ApiResponse<Object>> handleGeneralException(
                        GeneralException e) {
                BaseErrorCode errorCode = e.getErrorCode();

                log.warn(
                                "GeneralException: code={}, message={}",
                                errorCode.getCode(),
                                errorCode.getMessage());

                return ResponseEntity
                                .status(errorCode.getHttpStatus())
                                .body(ApiResponse.onFailure(
                                                errorCode,
                                                e.getMessage()));
        }

        /**
         * @RequestBody에 대한 @Valid 검증 실패 처리
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Object>> handleValidationException(
                        MethodArgumentNotValidException e) {
                List<String> errors = e.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(fieldError -> String.format(
                                                "[%s] %s (입력값: %s)",
                                                fieldError.getField(),
                                                fieldError.getDefaultMessage(),
                                                fieldError.getRejectedValue()))
                                .toList();

                log.warn("Request body validation failed: {}", errors);

                BaseErrorCode errorCode = GeneralErrorCode.INVALID_PARAMETER;

                return ResponseEntity
                                .status(errorCode.getHttpStatus())
                                .body(ApiResponse.onFailure(
                                                errorCode,
                                                errors));
        }

        /**
         * @PathVariable, @RequestParam 등의 제약 조건 검증 실패 처리
         */
        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiResponse<Object>> handleConstraintViolationException(
                        ConstraintViolationException e) {
                List<String> errors = e.getConstraintViolations()
                                .stream()
                                .map(violation -> String.format(
                                                "[%s] %s (입력값: %s)",
                                                violation.getPropertyPath(),
                                                violation.getMessage(),
                                                violation.getInvalidValue()))
                                .toList();

                log.warn("Parameter validation failed: {}", errors);

                BaseErrorCode errorCode = GeneralErrorCode.INVALID_PARAMETER;

                return ResponseEntity
                                .status(errorCode.getHttpStatus())
                                .body(ApiResponse.onFailure(
                                                errorCode,
                                                errors));
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<ApiResponse<Object>> handleAuthenticationException(
                        AuthenticationException e) {
                BaseErrorCode errorCode = AuthErrorCode.LOGIN_FAILED;

                log.warn("Login failed");

                return ResponseEntity
                                .status(errorCode.getHttpStatus())
                                .body(ApiResponse.onFailure(errorCode, null));
        }

        /**
         * JSON 문법 오류 또는 타입 불일치 처리
         */
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiResponse<Object>> handleJsonException(
                        HttpMessageNotReadableException e) {
                log.warn("JSON parse error: {}", e.getMessage());

                BaseErrorCode errorCode = GeneralErrorCode.INVALID_PARAMETER;

                return ResponseEntity
                                .status(errorCode.getHttpStatus())
                                .body(ApiResponse.onFailure(
                                                errorCode,
                                                "입력값이 잘못되었습니다. JSON 형식을 확인해주세요."));
        }

        /**
         * 서비스에서 던진 ResponseStatusException 처리 (404 · 409 등 상태 코드를 그대로 전달)
         * — 없으면 아래 Exception 처리로 넘어가 모두 500이 됨
         */
        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<ApiResponse<Object>> handleResponseStatusException(
                        ResponseStatusException e) {
                int status = e.getStatusCode().value();

                if (status >= 500) {
                        log.error("ResponseStatusException: status={}", status, e);
                } else {
                        log.warn("ResponseStatusException: status={}, reason={}", status, e.getReason());
                }

                return ResponseEntity
                                .status(e.getStatusCode())
                                .body(new ApiResponse<Object>(
                                                false,
                                                "HTTP_" + status,
                                                e.getReason() == null ? "요청을 처리할 수 없습니다." : e.getReason(),
                                                null,
                                                null));
        }

        /**
         * 별도로 처리되지 않은 모든 예외 처리
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Object>> handleException(
                        Exception e) {
                log.error("Unhandled exception occurred", e);

                BaseErrorCode errorCode = GeneralErrorCode.INTERNAL_SERVER_ERROR;

                return ResponseEntity
                                .status(errorCode.getHttpStatus())
                                .body(ApiResponse.onFailure(
                                                errorCode,
                                                "서버 내부 오류가 발생했습니다."));
        }
}