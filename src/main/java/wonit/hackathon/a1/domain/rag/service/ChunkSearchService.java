package wonit.hackathon.a1.domain.rag.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import wonit.hackathon.a1.domain.insurance.enums.DocumentType;
import wonit.hackathon.a1.domain.rag.client.EmbeddingClient;
import wonit.hackathon.a1.domain.rag.dto.SearchRequest;
import wonit.hackathon.a1.domain.rag.dto.SearchResult;
import wonit.hackathon.a1.domain.rag.repository.ChunkSearchRepository;

/** 벡터 검색 — 같은 검색어는 한 요청 안에서 한 번만 임베딩 (OpenAI 호출 · 시간 절약) */
@Service
public class ChunkSearchService {

    private final EmbeddingClient embeddings;
    private final ChunkSearchRepository repository;

    public ChunkSearchService(EmbeddingClient embeddings, ChunkSearchRepository repository) {
        this.embeddings = embeddings;
        this.repository = repository;
    }

    /** 한 질문 처리 동안 쓰는 검색기 (검색어 → 벡터 캐시) */
    public Searcher newSearcher() {
        return new Searcher();
    }

    public class Searcher {

        private final Map<String, float[]> vectors = new HashMap<>();

        public List<SearchResult> search(String query, Long productId, DocumentType type, int topK) {
            float[] vector = vectors.computeIfAbsent(query.strip(), embeddings::embed);
            return repository.search(vector, new SearchRequest(List.of(productId), List.of(type), topK));
        }
    }
}