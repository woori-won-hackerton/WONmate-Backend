package wonit.hackathon.a1.domain.rag.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import wonit.hackathon.a1.domain.rag.client.EmbeddingClient;
import wonit.hackathon.a1.domain.rag.dto.IngestionReport;
import wonit.hackathon.a1.domain.rag.dto.IngestionResult;
import wonit.hackathon.a1.domain.rag.ingest.ArticleChunker;
import wonit.hackathon.a1.domain.rag.ingest.PdfExtractor;
import wonit.hackathon.a1.domain.rag.repository.DocumentIngestionRepository;

/**
 * 문서 적재: documents/{상품 폴더}/metadata.json → PDF 추출 → 청크 → 임베딩 → DB 저장
 * - 같은 파일(SHA-256)로 이미 적재했으면 건너뜀 → 여러 번 실행해도 안전
 * - 이 클래스에는 @Transactional 을 붙이지 않음 (임베딩 대기 중 DB 연결을 잡고 있지 않도록)
 *
 * metadata.json 예시
 * {
 * "productId": null,
 * "productName": "무배당우리WON하는암보험",
 * "documents": [
 * {"fileName": "통합약관_무배당우리WON하는암보험_20260701.pdf", "documentType": "TERMS"},
 * {"fileName": "상품요약서_무배당우리WON하는암보험_20260701.pdf", "documentType": "SUMMARY"}
 * ]
 * }
 */
@Slf4j
@Service
public class DocumentIngestionService {

    private final EmbeddingClient embeddings;
    private final DocumentIngestionRepository repository;
    private final PdfExtractor extractor;
    private final ArticleChunker chunker;
    private final JsonMapper mapper;
    private final Path root;

    public DocumentIngestionService(
            EmbeddingClient embeddings,
            DocumentIngestionRepository repository,
            PdfExtractor extractor,
            ArticleChunker chunker,
            JsonMapper mapper,
            @Value("${rag.documents-dir:documents}") String root) {
        this.embeddings = embeddings;
        this.repository = repository;
        this.extractor = extractor;
        this.chunker = chunker;
        this.mapper = mapper;
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    /** 문서 폴더 전체 적재 (동시에 한 번만 실행) */
    public synchronized IngestionReport ingestAll() throws java.io.IOException {
        List<IngestionResult> results = new ArrayList<>();

        if (!Files.isDirectory(root)) {
            log.warn("RAG documents directory not found: {}", root);
            return new IngestionReport(0, 0, 0, 0, repository.countChunks(), results);
        }

        List<Path> metadataFiles;
        try (Stream<Path> paths = Files.walk(root)) {
            metadataFiles = paths
                    .filter(p -> p.getFileName().toString().equals("metadata.json"))
                    .filter(p -> !p.getParent().getFileName().toString().equals("unmatched"))
                    .sorted()
                    .toList();
        }

        for (int i = 0; i < metadataFiles.size(); i++) {
            Path metadata = metadataFiles.get(i);
            String directory = root.relativize(metadata.getParent()).toString().replace('\\', '/');
            try {
                JsonNode meta = mapper.readTree(Files.readString(metadata));
                if (!meta.path("documents").isArray()) {
                    throw new IllegalArgumentException("Invalid metadata");
                }
                Long declaredId = meta.path("productId").isNull() || meta.path("productId").isMissingNode()
                        ? null
                        : meta.path("productId").asLong();
                Long productId = repository.resolveOrCreateProduct(declaredId, meta.path("productName").asString(""));

                JsonNode docs = meta.path("documents");
                for (int j = 0; j < docs.size(); j++) {
                    process(metadata.getParent(), directory, docs.get(j), productId, results);
                }
            } catch (Exception e) {
                // 메타데이터 오류는 그 폴더만 실패로 보고하고 다른 상품은 계속 처리
                results.add(failure(directory, e));
            }
        }

        long completed = results.stream().filter(r -> r.status().equals("COMPLETED")).count();
        long skipped = results.stream().filter(r -> r.status().equals("SKIPPED")).count();
        long failed = results.stream().filter(r -> r.status().equals("FAILED")).count();
        int chunks = results.stream().mapToInt(IngestionResult::chunks).sum();
        return new IngestionReport(completed, skipped, failed, chunks, repository.countChunks(), results);
    }

    private void process(Path dir, String directory, JsonNode doc, Long productId, List<IngestionResult> results) {
        String fileName = doc.path("fileName").asString("");
        String relative = directory + "/" + fileName;
        try {
            // ../ 나 심볼릭 링크로 상품 폴더 밖 파일을 읽지 못하게 실제 경로 확인
            Path pdf = dir.resolve(fileName).toRealPath();
            if (!pdf.getParent().equals(dir.toRealPath())
                    || !pdf.startsWith(root.toRealPath())
                    || !pdf.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                throw new IllegalArgumentException("Invalid PDF path");
            }

            DocumentType type = DocumentType.valueOf(doc.path("documentType").asString(""));
            String storageUrl = root.toRealPath().relativize(pdf).toString().replace('\\', '/');
            String hash = hash(pdf);

            if (repository.isCompleted(productId, type, storageUrl, hash)) {
                results.add(new IngestionResult(relative, "SKIPPED", 0, "Unchanged completed document", List.of()));
                return;
            }

            log.info("RAG ingest start: {}", relative);
            List<PdfExtractor.Page> pages = extractor.extract(pdf);
            List<ArticleChunker.Chunk> chunks = chunker.chunk(pages);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("No extractable text; OCR may be required");
            }

            // 벡터를 모두 준비한 뒤에만 기존 데이터를 교체
            List<String> contents = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                contents.add(chunks.get(i).content());
            }
            List<float[]> vectors = embeddings.embedAll(contents);
            if (vectors.size() != chunks.size()) {
                throw new IllegalStateException("Embedding count mismatch");
            }
            if (!hash.equals(hash(pdf))) {
                throw new IllegalStateException("PDF changed during processing");
            }

            boolean saved = repository.replace(productId, type, storageUrl, pdf.getFileName().toString(), hash, chunks,
                    vectors);
            List<Integer> ocrPages = pages.stream().filter(PdfExtractor.Page::needsOcr).map(PdfExtractor.Page::number)
                    .toList();
            results.add(new IngestionResult(relative, saved ? "COMPLETED" : "SKIPPED", saved ? chunks.size() : 0, null,
                    ocrPages));
            log.info("RAG ingest done: {} ({} chunks)", relative, chunks.size());
        } catch (Exception e) {
            results.add(failure(relative, e));
        }
    }

    private IngestionResult failure(String path, Exception e) {
        // 외부 응답이 섞이지 않도록 예외 종류만 남김
        log.warn("RAG ingest failed: path={} reason={}", path, e.getClass().getSimpleName());
        return new IngestionResult(path, "FAILED", 0, e.getClass().getSimpleName(), List.of());
    }

    private static String hash(Path pdf) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(pdf)) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}