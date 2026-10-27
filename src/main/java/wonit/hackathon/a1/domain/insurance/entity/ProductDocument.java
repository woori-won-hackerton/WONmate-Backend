package wonit.hackathon.a1.domain.insurance.entity;

import wonit.hackathon.a1.domain.insurance.entity.InsuranceProduct;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import wonit.hackathon.a1.global.common.BaseEntity;

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
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "document_type", nullable = false, columnDefinition = "document_type")
    private DocumentType documentType;

    /** 만료되는 서명 URL 대신 영구 객체 키 또는 저장 위치를 보관합니다. */
    @Column(name = "storage_uri", nullable = false, columnDefinition = "text")
    private String storageUri;

    private ProductDocument(
            InsuranceProduct insuranceProduct,
            String documentName,
            DocumentType documentType,
            String storageUri
    ) {
        this.insuranceProduct = Objects.requireNonNull(insuranceProduct, "insuranceProduct");
        this.documentName = Objects.requireNonNull(documentName, "documentName");
        this.documentType = Objects.requireNonNull(documentType, "documentType");
        this.storageUri = Objects.requireNonNull(storageUri, "storageUri");
    }

    public static ProductDocument create(
            InsuranceProduct insuranceProduct,
            String documentName,
            DocumentType documentType,
            String storageUri
    ) {
        return new ProductDocument(
                insuranceProduct,
                documentName,
                documentType,
                storageUri
        );
    }
}
