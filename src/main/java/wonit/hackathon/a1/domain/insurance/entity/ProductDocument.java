package wonit.hackathon.a1.domain.insurance.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import wonit.hackathon.a1.domain.insurance.enums.IngestionStatus;
import wonit.hackathon.a1.global.common.BaseEntity;

/** 보험상품에 속한 원본 PDF의 위치, 식별 정보와 처리 상태를 관리한다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_documents")
public class ProductDocument extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private InsuranceProduct insuranceProduct;

    @Column(name = "document_name", nullable = false, length = 255)
    private String documentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    /** 프로젝트 루트 기준 PDF 상대 경로. 원본 파일명은 그대로 유지한다. */
    @Column(name = "storage_url", nullable = false, columnDefinition = "text")
    private String storageUrl;

    /** PDF 바이너리에서 계산한 SHA-256. 파일명이나 상품명으로 대체하지 않는다. */
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;

    @Column(name = "document_version", length = 100)
    private String documentVersion;

    /** 문서에서 적용일이 확인된 경우에만 기록한다. 파일명 날짜는 적용일로 간주하지 않는다. */
    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    // 신규 문서는 대기 상태로 등록한다. 실제 처리와 상태 전이는 이후 적재 단계에서 수행한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "ingestion_status", nullable = false, length = 20)
    private IngestionStatus ingestionStatus = IngestionStatus.PENDING;

    @Column(name = "embedding_model", length = 255)
    private String embeddingModel;

    private ProductDocument(InsuranceProduct insuranceProduct, String documentName,
                            DocumentType documentType, String storageUrl, String fileHash,
                            String documentVersion, LocalDate effectiveDate) {
        this.insuranceProduct = Objects.requireNonNull(insuranceProduct, "insuranceProduct");
        this.documentName = Objects.requireNonNull(documentName, "documentName");
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.storageUrl = Objects.requireNonNull(storageUrl, "storageUrl");
        if (fileHash == null || !fileHash.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("fileHash must be a SHA-256 hexadecimal digest");
        }
        this.fileHash = fileHash;
        this.documentVersion = documentVersion;
        this.effectiveDate = effectiveDate;
    }

    public static ProductDocument create(InsuranceProduct insuranceProduct, String documentName,
                                         DocumentType documentType, String storageUrl, String fileHash,
                                         String documentVersion, LocalDate effectiveDate) {
        return new ProductDocument(insuranceProduct, documentName, documentType, storageUrl,
                fileHash, documentVersion, effectiveDate);
    }

    public void updateIngestionStatus(IngestionStatus ingestionStatus) {
        this.ingestionStatus = Objects.requireNonNull(ingestionStatus, "ingestionStatus");
    }

    public void updateEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
}
