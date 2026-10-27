package wonit.hackathon.a1.domain.rag.repository;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import wonit.hackathon.a1.domain.insurance.entity.ProductChunk;
import wonit.hackathon.a1.domain.insurance.entity.ProductDocument;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import wonit.hackathon.a1.domain.rag.dto.SearchRequest;
import wonit.hackathon.a1.domain.rag.dto.SearchResult;

/** pgvector 코사인 거리로 청크 검색 (적재 완료 · 현재 임베딩 모델 문서만) */
@Repository
public class ChunkSearchRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public ChunkSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(float[] vector, SearchRequest request) {
        if (vector == null || vector.length != ProductChunk.DIMENSIONS) {
            throw new IllegalArgumentException("Invalid vector dimension");
        }

        // MATERIALIZED: 상품 · 문서 종류 조건을 먼저 적용한 뒤 정확한 거리로 정렬
        StringBuilder sql = new StringBuilder("""
                WITH candidates AS MATERIALIZED (
                    SELECT c.chunk_id, c.document_id, c.section_title, c.section_path,
                           c.start_page, c.end_page, c.content, c.embedding,
                           d.product_id, d.document_name, d.document_type, p.product_name
                    FROM product_chunks c
                    JOIN product_documents d ON d.document_id = c.document_id
                    JOIN insurance_products p ON p.product_id = d.product_id
                    WHERE c.embedding IS NOT NULL
                      AND d.ingestion_status = 'COMPLETED'
                      AND d.embedding_model = :model
                """);

        Map<String, Object> params = new HashMap<>();
        params.put("model", ProductDocument.EMBEDDING_MODEL);
        params.put("vector", Arrays.toString(vector));
        params.put("topK", request.topK());

        if (request.productIds() != null && !request.productIds().isEmpty()) {
            sql.append(" AND d.product_id IN (:productIds)");
            params.put("productIds", request.productIds());
        }
        if (request.documentTypes() != null && !request.documentTypes().isEmpty()) {
            sql.append(" AND d.document_type IN (:documentTypes)");
            params.put("documentTypes", request.documentTypes().stream().map(Enum::name).toList());
        }

        sql.append("""
                )
                SELECT chunk_id, document_id, product_id, product_name, document_name, document_type,
                       section_title, section_path, start_page, end_page, content,
                       1 - (embedding <=> CAST(:vector AS vector)) AS similarity_score
                FROM candidates
                ORDER BY embedding <=> CAST(:vector AS vector), chunk_id
                LIMIT :topK
                """);

        return jdbc.query(sql.toString(), params, (rs, row) -> new SearchResult(
                rs.getLong("chunk_id"),
                rs.getLong("document_id"),
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getString("document_name"),
                DocumentType.valueOf(rs.getString("document_type")),
                rs.getString("section_title"),
                rs.getString("section_path"),
                rs.getInt("start_page"),
                rs.getInt("end_page"),
                rs.getString("content"),
                rs.getDouble("similarity_score")));
    }
}