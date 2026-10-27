package wonit.hackathon.a1.domain.rag.repository;

import java.util.*;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import wonit.hackathon.a1.domain.rag.client.EmbeddingClient;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import wonit.hackathon.a1.domain.rag.dto.*;

@Repository
public class ChunkSearchRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public ChunkSearchRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<SearchResult> search(float[] vector, SearchRequest request) {
        if (vector == null || vector.length != 1536) throw new IllegalArgumentException("Invalid vector dimension");
        double norm = 0;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("Nonfinite vector");
            norm += (double) value * value;
        }
        if (norm == 0) throw new IllegalArgumentException("Zero query vector");
        // MATERIALIZED prevents an approximate vector index from serving the ORDER BY.
        // Product/type predicates are applied in SQL before exact distance ranking.
        var sql = new StringBuilder("""
                WITH candidates AS MATERIALIZED (
                    SELECT c.*, d.product_id, d.document_name, d.document_type
                    FROM product_chunks c JOIN product_documents d ON d.document_id = c.document_id
                    WHERE c.embedding IS NOT NULL AND vector_norm(c.embedding) > 0
                      AND d.ingestion_status = 'COMPLETED' AND d.embedding_model = :model
                """);
        var params = new HashMap<String, Object>();
        params.put("model", EmbeddingClient.MODEL);
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
                ) SELECT chunk_id, document_id, product_id, document_name, document_type,
                    section_title, section_path, start_page, end_page, content,
                    1 - (embedding <=> CAST(:vector AS vector)) AS similarity_score
                  FROM candidates
                  ORDER BY embedding <=> CAST(:vector AS vector), chunk_id
                  LIMIT :topK
                """);
        return jdbc.query(sql.toString(), params, (rs, row) -> new SearchResult(
                rs.getLong("chunk_id"), rs.getLong("document_id"), rs.getLong("product_id"),
                rs.getString("document_name"), DocumentType.valueOf(rs.getString("document_type")),
                rs.getString("section_title"), rs.getString("section_path"), rs.getInt("start_page"),
                rs.getInt("end_page"), rs.getString("content"), rs.getDouble("similarity_score")));
    }
}
