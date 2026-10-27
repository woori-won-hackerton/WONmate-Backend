package wonit.hackathon.a1.domain.insurance.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wonit.hackathon.a1.domain.insurance.dto.response.InsuranceProductResponse;
import wonit.hackathon.a1.domain.insurance.repository.InsuranceProductRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InsuranceProductService {
    private final InsuranceProductRepository insuranceProductRepository;

    public List<InsuranceProductResponse> getProducts() {
        return insuranceProductRepository.findProductOptions();
    }
}
