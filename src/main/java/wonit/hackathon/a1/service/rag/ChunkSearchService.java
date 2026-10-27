package wonit.hackathon.a1.service.rag;

import java.util.List;
import org.springframework.stereotype.Service;
import wonit.hackathon.a1.client.rag.EmbeddingClient;
import wonit.hackathon.a1.dto.rag.*;
import wonit.hackathon.a1.repository.rag.ChunkSearchRepository;

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
