package wonit.hackathon.a1.controller.rag;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import wonit.hackathon.a1.service.rag.DocumentIngestionService;
import wonit.hackathon.a1.dto.rag.IngestionReport;
import wonit.hackathon.a1.dto.rag.DocumentSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 로컬 문서 적재와 저장 결과 조회 API. 파일 업로드 대신 서버의 documents 폴더를 읽는다. */
@RestController
@Profile("local & !prod & !production")
@Tag(name = "RAG 문서 적재", description = "로컬 PDF → 청킹 → 임베딩 → PostgreSQL 저장 및 문서 조회")
public class DocumentController {
    private final DocumentIngestionService service;
    public DocumentController(DocumentIngestionService service) { this.service = service; }

    @PostMapping("/api/admin/documents/ingest")
    @Operation(summary = "신규·변경 PDF 적재", description = "요청 본문 없이 쿼리로 범위를 지정합니다. 둘 다 비우면 전체 문서를 처리합니다. "
            + "동기 처리이며 문서별 실패는 HTTP 200 응답의 failed와 documents에 표시합니다. 로컬 직접 접속만 허용합니다.")
    public IngestionReport ingest(HttpServletRequest request,
            @Parameter(description = "기존 DB에 매핑된 상품 ID 필터. 매핑을 생성하거나 덮어쓰지 않습니다.")
            @RequestParam(required = false) Long productId,
            @Parameter(description = "documents/ 기준 상대 경로. 한글 원문을 입력하고 documents/ 접두사는 제외합니다.",
                    example = "product_01/상품요약서_무배당엔젤쑥쑥어린이보장보험_20260901_00.pdf")
            @RequestParam(required = false) String documentPath) throws java.io.IOException {
        try {
            // 전달된 X-Forwarded-For가 아닌 직접 연결 주소로 검사한다. 서버도 loopback에 바인딩한다.
            if (!InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress())
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        } catch (java.net.UnknownHostException e) { throw new ResponseStatusException(HttpStatus.FORBIDDEN); }
        return service.ingest(productId, documentPath);
    }

    @GetMapping("/api/documents")
    @Operation(summary = "저장된 문서 목록 조회", description = "문서별 상품 ID·현재 저장 상태·SHA-256·청크 수를 반환합니다. "
            + "적재 시도의 실패 이력은 POST 응답에서 확인하며, 이 API는 DB에 남아 있는 문서만 조회합니다.")
    public List<DocumentSummary> list() { return service.list(); }
}
