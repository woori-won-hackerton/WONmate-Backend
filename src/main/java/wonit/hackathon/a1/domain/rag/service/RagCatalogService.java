package wonit.hackathon.a1.domain.rag.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import wonit.hackathon.a1.domain.insurance.entity.ProductDocument;
import wonit.hackathon.a1.domain.rag.dto.DocumentResponse;
import wonit.hackathon.a1.domain.rag.dto.ProductResponse;
import wonit.hackathon.a1.domain.rag.ingest.ProductCategory;
import wonit.hackathon.a1.domain.rag.repository.RagCatalogRepository;

/** 챗봇 상품 목록 · 지식 자료실 문서 목록 · 원본 PDF */
@Service
public class RagCatalogService {

    private final RagCatalogRepository repository;
    private final Path root;

    public RagCatalogService(
            RagCatalogRepository repository,
            @Value("${rag.documents-dir:documents}") String root) {
        this.repository = repository;
        this.root = Path.of(root).toAbsolutePath().normalize();
    }

    /** 챗봇에서 고를 수 있는 상품 (대분류 · 상품명 순) */
    public List<ProductResponse> getProducts() {
        List<Object[]> rows = repository.findProductsWithCompletedDocuments();
        List<ProductResponse> products = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] row = rows.get(i);
            String name = (String) row[1];
            products.add(new ProductResponse(
                    ((Number) row[0]).longValue(),
                    name,
                    ProductCategory.of((String) row[2], name),
                    ((Number) row[3]).longValue()));
        }
        products.sort((a, b) -> {
            int byCategory = a.category().compareTo(b.category());
            return byCategory != 0 ? byCategory : a.productName().compareTo(b.productName());
        });
        return products;
    }

    /** 지식 자료실 문서 목록 */
    public List<DocumentResponse> getDocuments() {
        List<ProductDocument> documents = repository.findAllDocuments();
        Map<Long, Long> chunkCounts = repository.countChunksByDocument();
        List<DocumentResponse> result = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            ProductDocument d = documents.get(i);
            String productName = d.getInsuranceProduct().getProductName();
            result.add(new DocumentResponse(
                    d.getId(),
                    d.getInsuranceProduct().getId(),
                    productName,
                    ProductCategory.of(d.getInsuranceProduct().getInsuranceType(), productName),
                    DocumentTitle.of(productName, d.getDocumentType()),
                    d.getDocumentName(),
                    d.getDocumentType(),
                    d.getIngestionStatus(),
                    chunkCounts.getOrDefault(d.getId(), 0L),
                    d.getUpdatedAt()));
        }
        return result;
    }

    /** 원본 PDF 경로 — 문서 폴더 밖 파일은 열지 않음 */
    public DocumentFile getDocumentFile(Long documentId) {
        ProductDocument document = repository.findDocument(documentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "문서를 찾을 수 없습니다."));

        Path file = root.resolve(document.getStorageUrl()).normalize();
        if (!file.startsWith(root)
                || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")
                || !Files.isRegularFile(file)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "원본 파일이 서버에 없습니다.");
        }
        return new DocumentFile(file, document.getDocumentName());
    }

    public record DocumentFile(Path path, String fileName) {
    }
}