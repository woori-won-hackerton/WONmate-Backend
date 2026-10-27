package wonit.hackathon.a1.dto.rag;

import jakarta.validation.constraints.*;
import java.util.List;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;

public record SearchRequest(
        @NotBlank @Size(max = 2000) String question,
        @Size(max = 100) List<@NotNull @Positive Long> productIds,
        @Size(max = 10) List<@NotNull DocumentType> documentTypes,
        @Min(1) @Max(50) Integer topK) {
    public SearchRequest {
        if (topK == null) topK = 5;
    }
}
