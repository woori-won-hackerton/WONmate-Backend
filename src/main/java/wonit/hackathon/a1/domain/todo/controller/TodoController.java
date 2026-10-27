package wonit.hackathon.a1.domain.todo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import wonit.hackathon.a1.domain.todo.dto.request.TodoCreateRequest;
import wonit.hackathon.a1.domain.todo.dto.request.TodoStatusUpdateRequest;
import wonit.hackathon.a1.domain.todo.dto.response.TodoResponse;
import wonit.hackathon.a1.domain.todo.service.TodoService;
import wonit.hackathon.a1.global.apiPayload.ApiResponse;
import wonit.hackathon.a1.global.exception.GeneralException;
import wonit.hackathon.a1.global.security.code.AuthErrorCode;

import java.util.List;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/todos")
@Tag(name = "할 일 API")
public class TodoController {

    private final TodoService todoService;

    @PostMapping
    @Operation(summary = "내 할 일 등록")
    public ResponseEntity<ApiResponse<TodoResponse>> createTodo(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody TodoCreateRequest request
    ) {
        Long fcId = getAuthenticatedFcId(jwt);

        TodoResponse response = todoService.createTodo(fcId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(
                        "할 일이 등록되었습니다.",
                        response
                ));
    }

    @GetMapping
    @Operation(summary = "내 할 일 목록 조회")
    public ApiResponse<List<TodoResponse>> getTodos(
            @AuthenticationPrincipal Jwt jwt
    ) {
        Long fcId = getAuthenticatedFcId(jwt);

        return ApiResponse.onSuccess(
                "할 일 목록 조회에 성공했습니다.",
                todoService.getTodos(fcId)
        );
    }

    @PatchMapping("/{todoId}/status")
    @Operation(summary = "내 할 일 완료 상태 변경")
    public ApiResponse<TodoResponse> updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable("todoId") Long todoId,
            @Valid @RequestBody TodoStatusUpdateRequest request
    ) {
        Long fcId = getAuthenticatedFcId(jwt);

        return ApiResponse.onSuccess(
                "할 일 상태가 변경되었습니다.",
                todoService.updateStatus(fcId, todoId, request)
        );
    }

    private Long getAuthenticatedFcId(Jwt jwt) {
        if (jwt == null) {
            throw new GeneralException(
                    AuthErrorCode.TOKEN_NOT_EXIST
            );
        }

        try {
            long fcId = Long.parseLong(jwt.getSubject());
            if (fcId <= 0) {
                throw new NumberFormatException();
            }
            return fcId;
        } catch (NumberFormatException e) {
            throw new GeneralException(AuthErrorCode.TOKEN_INVALID);
        }
    }
}
