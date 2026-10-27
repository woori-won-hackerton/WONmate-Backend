package wonit.hackathon.a1.domain.insurance.entity;

import wonit.hackathon.a1.domain.insurance.entity.ProductDocument;
import jakarta.persistence.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "product_chunks", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"document_id", "chunk_index"})
})
@Check(constraints = "(chunk_index >= 0) AND (page_number IS NULL OR page_number >= 1)")
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

    @JdbcTypeCode(SqlTypes.VECTOR)
    @Array(length = 1536)
    @Column(name = "embedding", columnDefinition = "vector(1536)")
    private float[] embedding;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    private ProductChunk(
            ProductDocument productDocument,
            String content,
            float[] embedding,
            Integer pageNumber,
            Integer chunkIndex
    ) {
        this.productDocument = Objects.requireNonNull(productDocument, "productDocument");
        this.content = Objects.requireNonNull(content, "content");
        updateEmbedding(embedding);
        this.pageNumber = pageNumber;
        this.chunkIndex = Objects.requireNonNull(chunkIndex, "chunkIndex");
        if (chunkIndex < 0) throw new IllegalArgumentException("청크 순서는 0 이상이어야 합니다.");
        if (pageNumber != null && pageNumber < 1) throw new IllegalArgumentException("페이지는 1 이상이어야 합니다.");
    }

    public static ProductChunk create(
            ProductDocument productDocument,
            String content,
            float[] embedding,
            Integer pageNumber,
            Integer chunkIndex
    ) {
        return new ProductChunk(
                productDocument,
                content,
                embedding,
                pageNumber,
                chunkIndex
        );
    }

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
