package wonit.hackathon.a1.domain.rag.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import wonit.hackathon.a1.domain.rag.dto.AskRequest;
import wonit.hackathon.a1.domain.rag.dto.AskResponse;
import wonit.hackathon.a1.domain.rag.dto.DocumentResponse;
import wonit.hackathon.a1.domain.rag.dto.ProductResponse;
import wonit.hackathon.a1.domain.rag.service.AskService;
import wonit.hackathon.a1.domain.rag.service.RagCatalogService;
import wonit.hackathon.a1.global.apiPayload.ApiResponse;

@RestController
@RequiredArgsConstructor
@Tag(name = "AI 챗봇 (RAG)", description = "보험 약관 · 상품요약서 기반 질의응답, 상품 · 문서 목록")
public class RagController {

    private final AskService askService;
    private final RagCatalogService catalogService;

    @PostMapping("/api/rag/ask")
    @Operation(summary = "AI 챗봇 질문", description = "questionType: TERM_EXPLANATION(용어) · INSURANCE_CONCEPT(개념) · PRODUCT_EXPLANATION(상품). "
            + "상품 설명일 때만 productId(GET /api/products)가 필요합니다. "
            + "질문 · 답변은 저장하지 않습니다.")
    public ApiResponse<AskResponse> ask(@Valid @RequestBody AskRequest request) {
        return ApiResponse.onSuccess("답변을 만들었습니다.", askService.ask(request));
    }

    @GetMapping("/api/products")
    @Operation(summary = "챗봇 상품 목록", description = "적재 완료된 문서가 있는 상품만 (대분류 · 상품명 순)")
    public ApiResponse<List<ProductResponse>> getProducts() {
        return ApiResponse.onSuccess("상품 목록 조회에 성공했습니다.", catalogService.getProducts());
    }

    @GetMapping("/api/documents")
    @Operation(summary = "지식 자료실 문서 목록", description = "적재 상태 · 청크 수 포함")
    public ApiResponse<List<DocumentResponse>> getDocuments() {
        return ApiResponse.onSuccess("문서 목록 조회에 성공했습니다.", catalogService.getDocuments());
    }

    @GetMapping("/api/documents/{documentId}/file")
    @Operation(summary = "원본 PDF 보기", description = "브라우저에서 바로 열 수 있게 inline으로 내려줍니다.")
    public ResponseEntity<Resource> getDocumentFile(@PathVariable("documentId") Long documentId)
            throws MalformedURLException {
        RagCatalogService.DocumentFile file = catalogService.getDocumentFile(documentId);
        Resource resource = new UrlResource(file.path().toUri());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(file.fileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(resource);
    }
}