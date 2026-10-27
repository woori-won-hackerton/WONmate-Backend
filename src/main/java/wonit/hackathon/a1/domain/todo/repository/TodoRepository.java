package wonit.hackathon.a1.domain.todo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import wonit.hackathon.a1.domain.todo.entity.Todo;

import java.util.List;
import java.util.Optional;

public interface TodoRepository extends JpaRepository<Todo, Long> {

    List<Todo> findAllByUser_IdOrderByIdAsc(Long userId);

    Optional<Todo> findByIdAndUser_Id(Long id, Long userId);
}