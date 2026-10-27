package wonit.hackathon.a1.domain.rag.service;

import java.util.List;
import org.springframework.stereotype.Service;
import wonit.hackathon.a1.domain.rag.client.EmbeddingClient;
import wonit.hackathon.a1.domain.rag.dto.*;
import wonit.hackathon.a1.domain.rag.repository.ChunkSearchRepository;

@Service
public class ChunkSearchService {
    private final EmbeddingClient embedding;
    private final ChunkSearchRepository repository;
    public ChunkSearchService(EmbeddingClient embedding, ChunkSearchRepository repository) {
        this.embedding = embedding;
        this.repository = repository;
    }

    // OpenAI HTTP completes before the repository opens a DB transaction.
    public List<SearchResult> search(SearchRequest request) {
        var vector = embedding.embed(List.of(request.question().strip())).getFirst();
        return repository.search(vector, request);
    }
}
