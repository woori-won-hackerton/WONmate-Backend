package wonit.hackathon.a1.domain.customerwiki.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import wonit.hackathon.a1.domain.customerwiki.entity.CustomerWiki;

import java.util.Optional;

public interface CustomerWikiRepository extends JpaRepository<CustomerWiki, Long> {

    Optional<CustomerWiki> findByCustomer_Id(Long customerId);
}
