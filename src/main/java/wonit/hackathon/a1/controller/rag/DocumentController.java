package wonit.hackathon.a1.controller.rag;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import wonit.hackathon.a1.service.rag.DocumentIngestionService;
import wonit.hackathon.a1.dto.rag.DocumentSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Read-only local document listing. Loading documents is an explicit CLI operation. */
@RestController
@Profile("local & !prod & !production")
@Tag(name = "RAG 문서 조회", description = "DB에 적재된 문서 상태와 청크 수 조회")
public class DocumentController {
    private final DocumentIngestionService service;
    public DocumentController(DocumentIngestionService service) { this.service = service; }

    @GetMapping("/api/documents")
    @Operation(summary = "저장된 문서 목록 조회", description = "문서별 상품 ID·현재 저장 상태·SHA-256·청크 수를 반환합니다. "
            + "적재 결과는 ragIngest CLI 보고서에서 확인하며, 이 API는 DB에 남아 있는 문서만 조회합니다.")
    public List<DocumentSummary> list() { return service.list(); }
}
