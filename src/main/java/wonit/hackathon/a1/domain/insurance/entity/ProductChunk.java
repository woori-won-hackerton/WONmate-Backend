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

/** 문서 내 원문 구간과 출처 페이지를 보존한다. 청크 번호는 문서 안에서만 유일하다. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_chunks", uniqueConstraints = {
        @UniqueConstraint(name = "uk_product_chunks_document_index", columnNames = {"document_id", "chunk_index"})
})
@Check(constraints = "chunk_index >= 0 AND start_page >= 1 AND end_page >= start_page")
public class ProductChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chunk_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private ProductDocument productDocument;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    // hibernate-vector를 통해 PostgreSQL vector(1536)에 매핑한다. 임베딩 전에는 null을 허용한다.
    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    // PDF 페이지는 1부터 시작하며, 시작/종료 페이지를 모두 포함한다.
    @Column(name = "start_page", nullable = false)
    private Integer startPage;

    @Column(name = "end_page", nullable = false)
    private Integer endPage;

    @Column(name = "section_title", columnDefinition = "text")
    private String sectionTitle;

    @Column(name = "section_path", columnDefinition = "text")
    private String sectionPath;

    /** 문서 내 0부터 시작하는 순서. 서로 다른 문서에서는 같은 번호를 사용할 수 있다. */
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
            String sectionPath
    ) {
        this.productDocument = Objects.requireNonNull(productDocument, "productDocument");
        this.content = Objects.requireNonNull(content, "content");
        updateEmbedding(embedding);
        this.startPage = Objects.requireNonNull(startPage, "startPage");
        this.endPage = Objects.requireNonNull(endPage, "endPage");
        this.sectionTitle = sectionTitle;
        this.sectionPath = sectionPath;
        this.chunkIndex = Objects.requireNonNull(chunkIndex, "chunkIndex");
        if (chunkIndex < 0) throw new IllegalArgumentException("청크 순서는 0 이상이어야 합니다.");
        if (startPage < 1 || endPage < startPage) {
            throw new IllegalArgumentException("Pages must satisfy 1 <= startPage <= endPage");
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
            String sectionPath
    ) {
        return new ProductChunk(
                productDocument,
                content,
                embedding,
                startPage,
                endPage,
                chunkIndex,
                sectionTitle,
                sectionPath
        );
    }

    /** 차원과 유한값을 검증하고 복사하여 외부 배열 변경이 엔티티에 반영되지 않도록 한다. */
    public void updateEmbedding(float[] embedding) {
        if (embedding != null && embedding.length != 1536) {
            throw new IllegalArgumentException("임베딩은 1536차원이어야 합니다.");
        }
        if (embedding != null) {
            for (float value : embedding) {
                if (!Float.isFinite(value)) {
                    throw new IllegalArgumentException("임베딩 값은 유한한 숫자여야 합니다.");
                }
            }
        }
        this.embedding = embedding == null ? null : embedding.clone();
    }

    public float[] getEmbedding() {
        return embedding == null ? null : embedding.clone();
    }
}
