package wonit.hackathon.a1.domain.rag.service;

import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;
import wonit.hackathon.a1.domain.insurance.enums.InsuranceType;

/** Explicit classifications, versioned independently of ignored local PDFs. */
public final class ProductCategoryCatalog {
    private final tools.jackson.databind.JsonNode categories;

    public ProductCategoryCatalog() throws IOException {
        try (var input = new ClassPathResource("rag/product-categories.json").getInputStream()) {
            categories = JsonMapper.builder().build().readTree(input);
        }
    }

    public InsuranceType resolve(String productName, String declaredType) {
        if (declaredType != null && !declaredType.isBlank()) {
            return InsuranceType.valueOf(declaredType);
        }
        return InsuranceType.valueOf(categories.path(productName).asString("UNKNOWN"));
    }
}
