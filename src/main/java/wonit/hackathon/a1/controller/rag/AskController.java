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
    @Operation(summary = "보험약관 근거 질의응답", description = "질문 내용을 자동 해석합니다. productIds는 선택이며 생략/null/빈 배열이면 전체 상품에서 검색합니다. "
            + "특정 상품만 검색하려면 GET /api/documents에서 확인한 상품 ID 하나를 지정하세요. "
            + "topK 기본 5, 1~20. 검색된 상품별 제한 조항을 추가 검색하며 출처 수는 최대 2×topK입니다. 이력은 저장하지 않습니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(
                            name = "전체 상품에서 암 보장 검색", value = """
                            {"question":"암 관련 보장 내용 찾아줘","topK":5}
                            """))))
    public AskResponse ask(@Valid @RequestBody AskRequest request) { return service.ask(request); }
}
