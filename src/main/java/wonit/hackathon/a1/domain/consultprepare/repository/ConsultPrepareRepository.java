package wonit.hackathon.a1.domain.consultprepare.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import wonit.hackathon.a1.domain.consultprepare.entity.ConsultPrepare;

import java.util.Optional;

public interface ConsultPrepareRepository
        extends JpaRepository<ConsultPrepare, Long> {

    Optional<ConsultPrepare> findByConsult_Id(Long consultationId);
}