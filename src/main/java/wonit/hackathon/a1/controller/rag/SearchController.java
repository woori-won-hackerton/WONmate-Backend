package wonit.hackathon.a1.controller.rag;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import wonit.hackathon.a1.dto.rag.*;
import wonit.hackathon.a1.service.rag.ChunkSearchService;

@RestController
@Tag(name = "RAG 검색")
public class SearchController {
    private final ChunkSearchService service;
    public SearchController(ChunkSearchService service) { this.service = service; }

    @PostMapping("/api/rag/search")
    @Operation(summary = "보험 문서 코사인 유사도 검색", description = "질문을 임베딩하고 원문과 PDF 페이지를 반환합니다. "
            + "productIds/documentTypes 생략 또는 빈 배열은 전체 범위입니다. topK 기본 5, 범위 1~50. "
            + "similarityScore는 1-코사인 거리이며 확률이 아닙니다. GPT 답변은 생성하지 않습니다.")
    public List<SearchResult> search(@Valid @RequestBody SearchRequest request) {
        return service.search(request);
    }
}
