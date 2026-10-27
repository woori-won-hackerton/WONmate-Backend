package wonit.hackathon.a1.domain.rag.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import wonit.hackathon.a1.domain.rag.dto.*;
import wonit.hackathon.a1.domain.rag.service.AskService;

@RestController
@Tag(name = "RAG 질의응답")
public class AskController {
    private final AskService service;
    public AskController(AskService service) { this.service = service; }
    @PostMapping("/api/rag/ask")
    @Operation(summary = "용어·보험 개념·상품 설명", description = "questionType으로 질문 유형을 선택합니다. "
            + "TERM_EXPLANATION(용어), INSURANCE_CONCEPT(보험 개념), PRODUCT_EXPLANATION(상품 설명). "
            + "상품 설명에만 단일 productId가 필수이며 다른 유형에서는 생략해야 합니다. GET /api/products에서 상품 ID와 상품명을 확인합니다. "
            + "용어·개념 설명은 전체 상품 중 최대 5개 상품에서 설명 근거를 검색합니다. topK 기본 5, 1~20. 출처는 상품별 최대 3×topK입니다. 이력은 저장하지 않습니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            name = "상품 보장 설명", value = """
                            {"questionType":"PRODUCT_EXPLANATION","productId":14,"question":"암 진단비 지급 조건과 면책·감액 조건을 설명해줘","topK":5}
                            """))))
    public AskResponse ask(@Valid @RequestBody AskRequest request) { return service.ask(request); }
}
