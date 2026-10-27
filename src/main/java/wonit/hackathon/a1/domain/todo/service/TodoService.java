package wonit.hackathon.a1.domain.todo.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import wonit.hackathon.a1.domain.todo.code.TodoErrorCode;
import wonit.hackathon.a1.domain.todo.dto.request.TodoCreateRequest;
import wonit.hackathon.a1.domain.todo.dto.request.TodoStatusUpdateRequest;
import wonit.hackathon.a1.domain.todo.dto.response.TodoResponse;
import wonit.hackathon.a1.domain.todo.entity.Todo;
import wonit.hackathon.a1.domain.todo.repository.TodoRepository;
import wonit.hackathon.a1.domain.user.entity.User;
import wonit.hackathon.a1.domain.user.repository.UserRepository;
import wonit.hackathon.a1.global.code.GeneralErrorCode;
import wonit.hackathon.a1.global.exception.GeneralException;

import java.util.List;


@Service
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    private final TodoRepository todoRepository;
    private final UserRepository userRepository;


    @Transactional
    public TodoResponse createTodo(
            @NotNull @Positive Long fcId,
            @NotNull @Valid TodoCreateRequest request
    ) {
        User user = userRepository.findById(fcId)
                .orElseThrow(() -> new GeneralException(
                        GeneralErrorCode.USER_NOT_FOUND
                ));

        Todo todo = Todo.create(
                request.content().strip(),
                null,
                user
        );

        Todo savedTodo = todoRepository.save(todo);

        return TodoResponse.from(savedTodo);
    }


    public List<TodoResponse> getTodos(
            @NotNull @Positive Long fcId
    ) {
        return todoRepository.findAllByUser_IdOrderByIdAsc(fcId)
                .stream()
                .map(TodoResponse::from)
                .toList();
    }


    @Transactional
    public TodoResponse updateStatus(
            @NotNull @Positive Long fcId,
            @NotNull @Positive Long todoId,
            @NotNull @Valid TodoStatusUpdateRequest request
    ) {
        Todo todo = todoRepository.findByIdAndUser_Id(todoId, fcId)
                .orElseThrow(() -> new GeneralException(
                        TodoErrorCode.TODO_NOT_FOUND
                ));

        if (Boolean.TRUE.equals(request.completed())) {
            todo.complete();
        } else {
            todo.reopen();
        }

        
        return TodoResponse.from(todo);
    }
}