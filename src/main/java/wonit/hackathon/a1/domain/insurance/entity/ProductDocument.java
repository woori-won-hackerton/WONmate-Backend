package wonit.hackathon.a1.domain.insurance.entity;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import wonit.hackathon.a1.domain.insurance.enums.IngestionStatus;
import wonit.hackathon.a1.global.common.BaseEntity;

/** 보험상품에 속한 원본 PDF(약관 · 상품요약서)의 위치, 해시, 적재 상태 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_documents")
public class ProductDocument extends BaseEntity {

    /** 임베딩 모델 — 바뀌면 기존 문서를 다시 적재해야 함 */
    public static final String EMBEDDING_MODEL = "text-embedding-3-small";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private InsuranceProduct insuranceProduct;

    /** 원본 PDF 파일명 */
    @Column(name = "document_name", nullable = false, length = 255)
    private String documentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    /** 문서 폴더(rag.documents-dir) 기준 상대 경로. 예) product_01/통합약관_….pdf */
    @Column(name = "storage_url", nullable = false, columnDefinition = "text")
    private String storageUrl;

    /** PDF 바이너리의 SHA-256 (같은 파일이면 다시 적재하지 않음) */
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_status", nullable = false, length = 20)
    private IngestionStatus ingestionStatus = IngestionStatus.PENDING;

    @Column(name = "embedding_model", length = 255)
    private String embeddingModel;

    private ProductDocument(
            InsuranceProduct insuranceProduct,
            String documentName,
            DocumentType documentType,
            String storageUrl,
            String fileHash) {
        this.insuranceProduct = Objects.requireNonNull(insuranceProduct, "insuranceProduct");
        this.documentName = Objects.requireNonNull(documentName, "documentName");
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.storageUrl = Objects.requireNonNull(storageUrl, "storageUrl");
        this.fileHash = validateHash(fileHash);
    }

    public static ProductDocument create(
            InsuranceProduct insuranceProduct,
            String documentName,
            DocumentType documentType,
            String storageUrl,
            String fileHash) {
        return new ProductDocument(insuranceProduct, documentName, documentType, storageUrl, fileHash);
    }

    /** 새 청크 저장과 같은 트랜잭션에서 호출 — 해시 · 모델 · 완료 상태를 함께 갱신 */
    public void completeReplacement(String documentName, String hash) {
        this.documentName = Objects.requireNonNull(documentName, "documentName");
        this.fileHash = validateHash(hash);
        this.embeddingModel = EMBEDDING_MODEL;
        this.ingestionStatus = IngestionStatus.COMPLETED;
    }

    public boolean isCompletedWith(String hash) {
        return ingestionStatus == IngestionStatus.COMPLETED
                && EMBEDDING_MODEL.equals(embeddingModel)
                && fileHash.equals(hash);
    }

    private static String validateHash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("fileHash must be a lowercase SHA-256 hex digest");
        }
        return hash;
    }
}