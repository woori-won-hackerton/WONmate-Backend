package wonit.hackathon.a1.service.rag;

import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import wonit.hackathon.a1.client.rag.EmbeddingClient;
import wonit.hackathon.a1.dto.rag.*;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import wonit.hackathon.a1.rag.ArticleChunker;
import wonit.hackathon.a1.rag.PdfExtractor;
import wonit.hackathon.a1.repository.rag.DocumentIngestionRepository;

/**
 * 문서 탐색부터 임베딩까지 조율한다. 이 클래스에 @Transactional을 붙이지 않는다.
 * 외부 API 대기 중 DB 연결을 점유하지 않도록 조회·교체 트랜잭션은 Repository 호출 안에서만 연다.
 */
@Service
public class DocumentIngestionService {
    private final EmbeddingClient embeddings;
    private final DocumentIngestionRepository repository;
    private final Path root;
    private final PdfExtractor extractor;
    private final ArticleChunker chunker;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public DocumentIngestionService(EmbeddingClient embeddings, DocumentIngestionRepository repository,
            PdfExtractor extractor, ArticleChunker chunker,
            @Value("${rag.debug.documents:documents}") String root) {
        this.embeddings = embeddings;
        this.repository = repository;
        this.extractor = extractor;
        this.chunker = chunker;
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    /**
     * 한 인스턴스의 동시 적재 요청은 직렬화한다. 여러 프로세스의 중복 저장은 DB 잠금으로 방지한다.
     * productId와 documentPath는 AND 조건의 필터이며 상품 매핑을 강제로 지정하는 값이 아니다.
     */
    public synchronized IngestionReport ingest(Long productId, String documentPath) throws java.io.IOException {
        var results = new ArrayList<IngestionResult>();
        try (var paths = Files.walk(root)) {
            for (Path metadata : paths.filter(p -> p.getFileName().toString().equals("metadata.json")).sorted().toList()) {
                String directory = root.relativize(metadata.getParent()).toString().replace('\\', '/');
                if (directory.equals("unmatched")) continue;
                try {
                    var meta = mapper.readTree(Files.readString(metadata));
                    if (!meta.path("documents").isArray()) throw new IllegalArgumentException("Invalid metadata");
                    // 명시된 ID가 존재하지 않으면 상품명으로 우회하지 않고 매핑 필요로 보고한다.
                    Long declaredId = meta.path("productId").isNull() || meta.path("productId").isMissingNode()
                            ? null : meta.path("productId").asLong(-1);
                    Long mapped = repository.resolveProduct(declaredId, meta.path("productName").asString(""));
                    if (productId != null && !productId.equals(mapped)) continue;
                    for (var doc : meta.path("documents")) {
                        String relative = directory + "/" + doc.path("fileName").asString("");
                        if (documentPath != null && !documentPath.equals(relative)) continue;
                        if (mapped == null) {
                            results.add(new IngestionResult(relative, "MAPPING_REQUIRED", 0, "No unique existing product mapping", List.of()));
                            continue;
                        }
                        process(metadata.getParent(), doc, mapped, relative, results);
                    }
                // 메타데이터 오류는 해당 폴더의 실패로 보고하고 다른 상품 폴더 처리는 계속한다.
                } catch (Exception e) { results.add(failure(directory, e)); }
            }
        }
        return new IngestionReport(results.stream().filter(r -> r.status().equals("COMPLETED")).count(),
                results.stream().filter(r -> r.status().equals("SKIPPED")).count(),
                results.stream().filter(r -> r.status().equals("FAILED") || r.status().equals("MAPPING_REQUIRED")).count(),
                results.stream().filter(r -> r.status().equals("COMPLETED")).mapToInt(IngestionResult::chunks).sum(),
                repository.countChunks(), results);
    }

    private void process(Path dir, JsonNode doc, Long productId, String relative, List<IngestionResult> results) {
        try {
            // 실제 경로를 검사해 ../ 및 심볼릭 링크로 상품 폴더 밖의 PDF를 읽지 못하게 한다.
            Path pdf = dir.resolve(doc.path("fileName").asString("")).toRealPath();
            if (!pdf.getParent().equals(dir.toRealPath()) || !pdf.startsWith(root.toRealPath())
                    || !pdf.toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) throw new IllegalArgumentException("Invalid PDF path");
            String storage = root.getFileName() + "/" + relative;
            var type = DocumentType.valueOf(doc.path("documentType").asString(""));
            String hash = hash(pdf);
            if (repository.isCompleted(productId, type, storage, hash)) {
                results.add(new IngestionResult(relative, "SKIPPED", 0, "Unchanged completed document", List.of())); return;
            }
            var pages = extractor.extract(pdf);
            var chunks = chunker.chunk(pages);
            if (chunks.isEmpty()) throw new IllegalStateException("No extractable text; OCR may be required");
            // 모든 벡터가 준비되기 전에는 기존 문서와 청크를 수정하지 않는다.
            var vectors = embeddings.embed(chunks.stream().map(ArticleChunker.Chunk::content).toList());
            if (vectors.size() != chunks.size()) throw new IllegalStateException("Embedding count mismatch");
            // 처리 중 파일이 바뀌었다면 오래된 본문/벡터를 새 파일의 결과로 저장하지 않는다.
            if (!hash.equals(hash(pdf))) throw new IllegalStateException("PDF changed during processing");
            String version = nullable(doc, "version"), date = nullable(doc, "effectiveDate");
            LocalDate effective = date == null ? null : LocalDate.parse(date);
            boolean saved = repository.replace(productId, type, storage, pdf.getFileName().toString(), hash,
                    version, effective, chunks, vectors);
            results.add(new IngestionResult(relative, saved ? "COMPLETED" : "SKIPPED", saved ? chunks.size() : 0, null,
                    pages.stream().filter(PdfExtractor.Page::needsOcr).map(PdfExtractor.Page::number).toList()));
        } catch (Exception e) { results.add(failure(relative, e)); }
    }

    private IngestionResult failure(String path, Exception e) {
        // 예외 메시지나 스택에 외부 응답이 섞이지 않도록 오류 종류만 노출한다.
        LoggerFactory.getLogger(getClass()).warn("Document ingestion failed path={} category={}", path, e.getClass().getSimpleName());
        return new IngestionResult(path, "FAILED", 0, e.getClass().getSimpleName(), List.of());
    }

    private String nullable(JsonNode doc, String field) { return doc.path(field).isTextual() ? doc.path(field).asString() : null; }

    private String hash(Path pdf) throws Exception {
        // 큰 PDF 전체를 메모리에 올리지 않고 스트림으로 바이너리 해시를 계산한다.
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(pdf)) {
            byte[] buffer = new byte[65536]; int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    public List<DocumentSummary> list() { return repository.list(); }
}
