package wonit.hackathon.a1.domain.todo.dto.response;

import wonit.hackathon.a1.domain.todo.entity.Todo;

public record TodoResponse(
        Long todoId,
        String content,
        boolean completed
) {

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
                todo.getId(),
                todo.getContent(),
                todo.isCompleted()
        );
    }
}