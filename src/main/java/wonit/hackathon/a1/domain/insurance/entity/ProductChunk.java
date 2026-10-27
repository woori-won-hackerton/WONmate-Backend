package wonit.hackathon.a1.domain.insurance.entity;

import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import wonit.hackathon.a1.global.common.BaseEntity;

/** 문서의 원문 구간(청크) + 임베딩 + 출처 페이지. 청크 번호는 문서 안에서만 유일 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_chunks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_chunks_document_index", columnNames = { "document_id", "chunk_index" })
})
@Check(constraints = "chunk_index >= 0 AND start_page >= 1 AND end_page >= start_page")
public class ProductChunk extends BaseEntity {

    public static final int DIMENSIONS = 1536;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chunk_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private ProductDocument productDocument;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    // hibernate-vector로 PostgreSQL vector(1536)에 매핑
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = DIMENSIONS)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    /** PDF 실제 페이지 (1부터, 양 끝 포함) */
    @Column(name = "start_page", nullable = false)
    private Integer startPage;

    @Column(name = "end_page", nullable = false)
    private Integer endPage;

    /** 조항 제목 (예: 제3조(보험금의 지급사유)) */
    @Column(name = "section_title", columnDefinition = "text")
    private String sectionTitle;

    /** 특약 > 조항 경로 */
    @Column(name = "section_path", columnDefinition = "text")
    private String sectionPath;

    /** 문서 안에서 0부터 시작하는 순서 */
    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    private ProductChunk(
            ProductDocument productDocument,
            String content,
            float[] embedding,
            Integer startPage,
            Integer endPage,
            Integer chunkIndex,
            String sectionTitle,
            String sectionPath) {
        this.productDocument = Objects.requireNonNull(productDocument, "productDocument");
        this.content = Objects.requireNonNull(content, "content");
        this.embedding = validateEmbedding(embedding);
        this.startPage = Objects.requireNonNull(startPage, "startPage");
        this.endPage = Objects.requireNonNull(endPage, "endPage");
        this.chunkIndex = Objects.requireNonNull(chunkIndex, "chunkIndex");
        this.sectionTitle = sectionTitle;
        this.sectionPath = sectionPath;
        if (chunkIndex < 0) {
            throw new IllegalArgumentException("청크 순서는 0 이상이어야 합니다.");
        }
        if (startPage < 1 || endPage < startPage) {
            throw new IllegalArgumentException("페이지는 1 <= startPage <= endPage 이어야 합니다.");
        }
    }

    public static ProductChunk create(
            ProductDocument productDocument,
            String content,
            float[] embedding,
            Integer startPage,
            Integer endPage,
            Integer chunkIndex,
            String sectionTitle,
            String sectionPath) {
        return new ProductChunk(
                productDocument, content, embedding, startPage, endPage, chunkIndex, sectionTitle, sectionPath);
    }

    public float[] getEmbedding() {
        return embedding == null ? null : embedding.clone();
    }

    private static float[] validateEmbedding(float[] embedding) {
        if (embedding == null || embedding.length != DIMENSIONS) {
            throw new IllegalArgumentException("임베딩은 1536차원이어야 합니다.");
        }
        for (int i = 0; i < embedding.length; i++) {
            if (!Float.isFinite(embedding[i])) {
                throw new IllegalArgumentException("임베딩 값은 유한한 숫자여야 합니다.");
            }
        }
        return embedding.clone();
    }
}