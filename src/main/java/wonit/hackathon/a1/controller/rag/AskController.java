package wonit.hackathon.a1.controller.rag;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import wonit.hackathon.a1.dto.rag.*;
import wonit.hackathon.a1.service.rag.AskService;

@RestController
@Tag(name = "RAG 질의응답")
public class AskController {
    private final AskService service;
    public AskController(AskService service) { this.service = service; }
    @PostMapping("/api/rag/ask")
    @Operation(summary = "용어·보험 개념·상품 설명·비교·추천", description = "questionType으로 질문 유형을 선택합니다. "
            + "TERM_EXPLANATION(용어), INSURANCE_CONCEPT(보험 개념), PRODUCT_EXPLANATION(상품 설명), "
            + "PRODUCT_COMPARISON(상품 비교), PRODUCT_RECOMMENDATION(상품 추천). "
            + "상품 설명에만 단일 productId가 필수이며 다른 유형에서는 생략해야 합니다. GET /api/documents에서 상품 ID를 확인합니다. "
            + "나머지 유형은 전체 상품에서 최대 5개 후보를 탐색합니다. topK 기본 5, 1~20. 출처는 상품별 최대 3×topK입니다. 이력은 저장하지 않습니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            name = "고객 선호 기반 추천", value = """
                            {"questionType":"PRODUCT_RECOMMENDATION","question":"52세 고객이고 예산은 월 5만원, 암 진단비 우선으로 추천해줘","topK":5}
                            """))))
    public AskResponse ask(@Valid @RequestBody AskRequest request) { return service.ask(request); }
}
